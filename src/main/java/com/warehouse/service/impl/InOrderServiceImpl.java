package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.InOrderDTO;
import com.warehouse.entity.*;
import com.warehouse.mapper.*;
import com.warehouse.common.BusinessException;
import com.warehouse.service.InOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class InOrderServiceImpl implements InOrderService {

    private final InOrderMapper inOrderMapper;
    private final InOrderItemMapper inOrderItemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLedgerMapper ledgerMapper;
    private final StockSnapshotMapper snapshotMapper;

    @Override
    public Page<InOrder> page(int current, int size, String status, Long warehouseId, Long supplierId) {
        LambdaQueryWrapper<InOrder> q = new LambdaQueryWrapper<InOrder>()
                .eq(status != null, InOrder::getStatus, status)
                .eq(warehouseId != null, InOrder::getWarehouseId, warehouseId)
                .eq(supplierId != null, InOrder::getSupplierId, supplierId)
                .orderByDesc(InOrder::getCreateTime);
        return inOrderMapper.selectPage(new Page<>(current, size), q);
    }

    @Override
    @Transactional
    public Long create(InOrderDTO dto, Long operatorId) {
        InOrder order = new InOrder();
        order.setOrderNo(generateNo());
        order.setWarehouseId(dto.getWarehouseId());
        order.setSupplierId(dto.getSupplierId());
        order.setType(dto.getType());
        order.setStatus("DRAFT");
        order.setOperatorId(operatorId);
        order.setRemark(dto.getRemark());
        inOrderMapper.insert(order);
        if (dto.getItems() != null) {
            for (InOrderDTO.Item i : dto.getItems()) {
                InOrderItem item = new InOrderItem();
                item.setOrderId(order.getId());
                item.setProductId(i.getProductId());
                item.setPlanQty(i.getPlanQty() != null ? i.getPlanQty() : 0);
                item.setActualQty(i.getActualQty() != null ? i.getActualQty() : 0);
                item.setPrice(i.getPrice());
                inOrderItemMapper.insert(item);
            }
        }
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId, List<ConfirmItemDTO> actualItems, Long operatorId) {
        InOrder order = inOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("入库单不存在");
        if (!"DRAFT".equals(order.getStatus())) throw new BusinessException("该入库单已确认");

        List<InOrderItem> items = inOrderItemMapper.selectList(
                new LambdaQueryWrapper<InOrderItem>().eq(InOrderItem::getOrderId, orderId));

        if (actualItems != null && !actualItems.isEmpty()) {
            java.util.Map<Long, Integer> qtyMap = new java.util.HashMap<>();
            for (ConfirmItemDTO c : actualItems) qtyMap.put(c.getItemId(), c.getActualQty());
            for (InOrderItem item : items) {
                Integer qty = qtyMap.get(item.getId());
                if (qty != null) { item.setActualQty(qty); inOrderItemMapper.updateById(item); }
            }
        }

        for (InOrderItem item : items) {
            int qty = item.getActualQty() != null ? item.getActualQty() : 0;
            if (qty <= 0) continue;

            // 行锁：确保并发入库串行化
            Inventory inv = inventoryMapper.selectForUpdate(order.getWarehouseId(), item.getProductId());
            int beforeQty;
            if (inv == null) {
                inv = new Inventory();
                inv.setWarehouseId(order.getWarehouseId());
                inv.setProductId(item.getProductId());
                inv.setQty(qty); inv.setAlertQty(0);
                inventoryMapper.insert(inv);
                beforeQty = 0;
            } else {
                beforeQty = inv.getQty();
                inv.setQty(beforeQty + qty);
                if (inventoryMapper.updateById(inv) == 0)
                    throw new BusinessException("库存数据已被并发修改，请刷新后重试");
            }

            int afterQty = beforeQty + qty;

            // 追加流水（核心：只增不改）
            InventoryLedger entry = new InventoryLedger();
            entry.setId(UUID.randomUUID().toString());
            entry.setProductId(item.getProductId());
            entry.setLocationId(order.getWarehouseId());
            entry.setChangeQty(new BigDecimal(qty));
            entry.setType("inbound");
            entry.setDocumentNo(order.getOrderNo());
            entry.setOperator(String.valueOf(operatorId));
            entry.setOccurredAt(LocalDateTime.now());
            entry.setSynced(1);
            ledgerMapper.insert(entry);

            // 更新快照缓存
            snapshotMapper.upsert(item.getProductId(), order.getWarehouseId(),
                    new BigDecimal(afterQty), inv.getAlertQty() != null ? inv.getAlertQty() : 0);
        }

        order.setStatus("CONFIRMED");
        order.setConfirmTime(LocalDateTime.now());
        inOrderMapper.updateById(order);
    }

    @Override
    public List<InOrderItem> getItems(Long orderId) {
        return inOrderItemMapper.selectList(
                new LambdaQueryWrapper<InOrderItem>().eq(InOrderItem::getOrderId, orderId));
    }

    @Override
    @Transactional
    public void delete(Long orderId) {
        InOrder order = inOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("入库单不存在");

        if ("CONFIRMED".equals(order.getStatus())) {
            List<InOrderItem> items = inOrderItemMapper.selectList(
                    new LambdaQueryWrapper<InOrderItem>().eq(InOrderItem::getOrderId, orderId));
            for (InOrderItem item : items) {
                int actualQty = item.getActualQty() != null ? item.getActualQty() : 0;
                if (actualQty <= 0) continue;

                Inventory inv = inventoryMapper.selectForUpdate(order.getWarehouseId(), item.getProductId());
                int beforeQty = inv != null ? inv.getQty() : 0;
                if (beforeQty < actualQty)
                    throw new BusinessException("库存不足以撤销入库，当前库存：" + beforeQty + "，需撤回：" + actualQty);

                int afterQty = beforeQty - actualQty;
                inventoryMapper.updateQty(order.getWarehouseId(), item.getProductId(), -actualQty);

                // 追加撤销流水（负数）
                InventoryLedger entry = new InventoryLedger();
                entry.setId(UUID.randomUUID().toString());
                entry.setProductId(item.getProductId());
                entry.setLocationId(order.getWarehouseId());
                entry.setChangeQty(new BigDecimal(-actualQty));
                entry.setType("inbound_cancel");
                entry.setDocumentNo(order.getOrderNo());
                entry.setOperator("system");
                entry.setNote("撤销入库单 " + order.getOrderNo());
                entry.setOccurredAt(LocalDateTime.now());
                entry.setSynced(1);
                ledgerMapper.insert(entry);

                // 更新快照
                snapshotMapper.upsert(item.getProductId(), order.getWarehouseId(),
                        new BigDecimal(afterQty), inv != null && inv.getAlertQty() != null ? inv.getAlertQty() : 0);
            }
        }

        inOrderItemMapper.delete(new LambdaQueryWrapper<InOrderItem>().eq(InOrderItem::getOrderId, orderId));
        inOrderMapper.deleteById(orderId);
    }

    private String generateNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int rand = ThreadLocalRandom.current().nextInt(100, 1000);
        return String.format("IN%s%d", ts, rand);
    }
}