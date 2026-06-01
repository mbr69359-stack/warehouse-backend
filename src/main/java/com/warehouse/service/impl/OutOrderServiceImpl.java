package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.OutOrderDTO;
import com.warehouse.entity.*;
import com.warehouse.mapper.*;
import com.warehouse.service.OutOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OutOrderServiceImpl implements OutOrderService {

    private final OutOrderMapper outOrderMapper;
    private final OutOrderItemMapper outOrderItemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;

    @Override
    public Page<OutOrder> page(int current, int size, String status, Long warehouseId) {
        LambdaQueryWrapper<OutOrder> q = new LambdaQueryWrapper<OutOrder>()
                .eq(status != null, OutOrder::getStatus, status)
                .eq(warehouseId != null, OutOrder::getWarehouseId, warehouseId)
                .orderByDesc(OutOrder::getCreateTime);
        return outOrderMapper.selectPage(new Page<>(current, size), q);
    }

    @Override
    @Transactional
    public Long create(OutOrderDTO dto, Long operatorId) {
        OutOrder order = new OutOrder();
        order.setOrderNo(generateNo());
        order.setWarehouseId(dto.getWarehouseId());
        order.setType(dto.getType());
        order.setStatus("DRAFT");
        order.setOperatorId(operatorId);
        order.setRemark(dto.getRemark());
        order.setTargetWarehouseId(dto.getTargetWarehouseId());
        outOrderMapper.insert(order);
        if (dto.getItems() != null) {
            for (OutOrderDTO.Item i : dto.getItems()) {
                OutOrderItem item = new OutOrderItem();
                item.setOrderId(order.getId());
                item.setProductId(i.getProductId());
                item.setQty(i.getQty() != null ? i.getQty() : 0);
                item.setPrice(i.getPrice());
                outOrderItemMapper.insert(item);
            }
        }
        return order.getId();
    }

    @Override
    @Transactional
    public void confirm(Long orderId, List<ConfirmItemDTO> actualItems, Long operatorId) {
        OutOrder order = outOrderMapper.selectById(orderId);
        if (order == null) throw new RuntimeException("出库单不存在");
        if (!"DRAFT".equals(order.getStatus())) throw new RuntimeException("该出库单已确认");
        List<OutOrderItem> items = outOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutOrderItem>().eq(OutOrderItem::getOrderId, orderId));
        if (actualItems != null && !actualItems.isEmpty()) {
            java.util.Map<Long, Integer> qtyMap = new java.util.HashMap<>();
            for (ConfirmItemDTO c : actualItems) qtyMap.put(c.getItemId(), c.getActualQty());
            for (OutOrderItem item : items) {
                Integer qty = qtyMap.get(item.getId());
                if (qty != null) { item.setActualQty(qty); outOrderItemMapper.updateById(item); }
            }
        }
        for (OutOrderItem item : items) {
            int qty = item.getActualQty() != null && item.getActualQty() > 0 ? item.getActualQty() : item.getQty();
            if (qty <= 0) continue;
            Inventory inv = inventoryMapper.selectForUpdate(order.getWarehouseId(), item.getProductId());
            if (inv == null || inv.getQty() < qty) {
                throw new RuntimeException("商品ID " + item.getProductId() + " 库存不足，当前：" +
                        (inv == null ? 0 : inv.getQty()) + "，需要：" + qty);
            }
            int beforeQty = inv.getQty();
            inventoryMapper.updateQty(order.getWarehouseId(), item.getProductId(), -qty);
            InventoryLog log = new InventoryLog();
            log.setWarehouseId(order.getWarehouseId());
            log.setProductId(item.getProductId());
            log.setChangeQty(-qty); log.setBeforeQty(beforeQty); log.setAfterQty(beforeQty - qty);
            log.setType("OUT"); log.setRefOrderId(orderId);
            inventoryLogMapper.insert(log);
            // 调拨出库：同步增加目标仓库库存
            if ("TRANSFER".equals(order.getType()) && order.getTargetWarehouseId() != null) {
                Inventory targetInv = inventoryMapper.selectForUpdate(order.getTargetWarehouseId(), item.getProductId());
                int targetBefore = targetInv != null ? targetInv.getQty() : 0;
                inventoryMapper.upsertQty(order.getTargetWarehouseId(), item.getProductId(), qty);
                InventoryLog transferLog = new InventoryLog();
                transferLog.setWarehouseId(order.getTargetWarehouseId());
                transferLog.setProductId(item.getProductId());
                transferLog.setChangeQty(qty);
                transferLog.setBeforeQty(targetBefore);
                transferLog.setAfterQty(targetBefore + qty);
                transferLog.setType("IN");
                transferLog.setRefOrderId(orderId);
                inventoryLogMapper.insert(transferLog);
            }
        }
        order.setStatus("CONFIRMED");
        order.setConfirmTime(LocalDateTime.now());
        outOrderMapper.updateById(order);
    }

    @Override
    public List<OutOrderItem> getItems(Long orderId) {
        return outOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutOrderItem>().eq(OutOrderItem::getOrderId, orderId));
    }

    @Override
    @Transactional
    public void delete(Long orderId) {
        OutOrder order = outOrderMapper.selectById(orderId);
        if (order == null) throw new RuntimeException("出库单不存在");
        if ("CONFIRMED".equals(order.getStatus())) {
            List<OutOrderItem> items = outOrderItemMapper.selectList(
                    new LambdaQueryWrapper<OutOrderItem>().eq(OutOrderItem::getOrderId, orderId));
            for (OutOrderItem item : items) {
                if (item.getQty() > 0) {
                    inventoryMapper.updateQty(order.getWarehouseId(), item.getProductId(), item.getQty());
                    // 调拨出库：回撤目标仓库库存
                    if ("TRANSFER".equals(order.getType()) && order.getTargetWarehouseId() != null) {
                        inventoryMapper.updateQty(order.getTargetWarehouseId(), item.getProductId(), -item.getQty());
                    }
                }
            }
            inventoryLogMapper.delete(new LambdaQueryWrapper<InventoryLog>().eq(InventoryLog::getRefOrderId, orderId));
        }
        outOrderItemMapper.delete(new LambdaQueryWrapper<OutOrderItem>().eq(OutOrderItem::getOrderId, orderId));
        outOrderMapper.deleteById(orderId);
    }

    private String generateNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int rand = ThreadLocalRandom.current().nextInt(100, 1000);
        return String.format("OUT%s%d", ts, rand);
    }
}
