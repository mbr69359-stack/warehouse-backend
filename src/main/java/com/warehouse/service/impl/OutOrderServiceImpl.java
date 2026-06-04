package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.OutOrderDTO;
import com.warehouse.entity.*;
import com.warehouse.mapper.*;
import com.warehouse.common.BusinessException;
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
    private final WarehouseMapper warehouseMapper;

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
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId, List<ConfirmItemDTO> actualItems, Long operatorId) {
        OutOrder order = outOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("出库单不存在");
        if (!"DRAFT".equals(order.getStatus())) throw new BusinessException("该出库单已确认");
        if ("TRANSFER".equals(order.getType())) {
            if (order.getTargetWarehouseId() == null) throw new BusinessException("调拨单缺少目标仓库");
            Warehouse targetWarehouse = warehouseMapper.selectById(order.getTargetWarehouseId());
            if (targetWarehouse == null) throw new BusinessException("目标仓库不存在或已删除");
            if (Integer.valueOf(0).equals(targetWarehouse.getStatus())) throw new BusinessException("目标仓库已禁用，无法入库");
        }
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
            int qty = item.getActualQty() != null ? item.getActualQty() : 0;
            if (qty <= 0) continue;
            Inventory inv = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                    .eq(Inventory::getWarehouseId, order.getWarehouseId())
                    .eq(Inventory::getProductId, item.getProductId()));
            if (inv == null || inv.getQty() < qty) {
                throw new BusinessException("商品ID " + item.getProductId() + " 库存不足，当前：" +
                        (inv == null ? 0 : inv.getQty()) + "，需要：" + qty);
            }
            int beforeQty = inv.getQty();
            inv.setQty(beforeQty - qty);
            if (inventoryMapper.updateById(inv) == 0)
                throw new BusinessException("库存数据已被并发修改，请刷新后重试");
            InventoryLog log = new InventoryLog();
            log.setWarehouseId(order.getWarehouseId());
            log.setProductId(item.getProductId());
            log.setChangeQty(-qty); log.setBeforeQty(beforeQty); log.setAfterQty(beforeQty - qty);
            log.setType("OUT"); log.setRefOrderId(orderId);
            inventoryLogMapper.insert(log);
            // 调拨出库：同步增加目标仓库库存
            if ("TRANSFER".equals(order.getType()) && order.getTargetWarehouseId() != null) {
                Inventory targetInv = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getWarehouseId, order.getTargetWarehouseId())
                        .eq(Inventory::getProductId, item.getProductId()));
                int targetBefore;
                if (targetInv == null) {
                    targetInv = new Inventory();
                    targetInv.setWarehouseId(order.getTargetWarehouseId());
                    targetInv.setProductId(item.getProductId());
                    targetInv.setQty(qty); targetInv.setAlertQty(0);
                    inventoryMapper.insert(targetInv);
                    targetBefore = 0;
                } else {
                    targetBefore = targetInv.getQty();
                    targetInv.setQty(targetBefore + qty);
                    if (inventoryMapper.updateById(targetInv) == 0)
                        throw new BusinessException("目标仓库库存数据已被并发修改，请刷新后重试");
                }
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
        if (order == null) throw new BusinessException("出库单不存在");
        if ("CONFIRMED".equals(order.getStatus())) {
            List<OutOrderItem> items = outOrderItemMapper.selectList(
                    new LambdaQueryWrapper<OutOrderItem>().eq(OutOrderItem::getOrderId, orderId));
            for (OutOrderItem item : items) {
                int restoreQty = (item.getActualQty() != null) ? item.getActualQty() : 0;
                if (restoreQty > 0) {
                    Inventory srcInv = inventoryMapper.selectForUpdate(order.getWarehouseId(), item.getProductId());
                    int srcBefore = srcInv != null ? srcInv.getQty() : 0;
                    inventoryMapper.updateQty(order.getWarehouseId(), item.getProductId(), restoreQty);
                    InventoryLog cancelLog = new InventoryLog();
                    cancelLog.setWarehouseId(order.getWarehouseId());
                    cancelLog.setProductId(item.getProductId());
                    cancelLog.setChangeQty(restoreQty);
                    cancelLog.setBeforeQty(srcBefore);
                    cancelLog.setAfterQty(srcBefore + restoreQty);
                    cancelLog.setType("OUT_CANCEL");
                    cancelLog.setRefOrderId(orderId);
                    inventoryLogMapper.insert(cancelLog);
                    if ("TRANSFER".equals(order.getType()) && order.getTargetWarehouseId() != null) {
                        Inventory tgtInv = inventoryMapper.selectForUpdate(order.getTargetWarehouseId(), item.getProductId());
                        int tgtBefore = tgtInv != null ? tgtInv.getQty() : 0;
                        inventoryMapper.updateQty(order.getTargetWarehouseId(), item.getProductId(), -restoreQty);
                        InventoryLog transferCancelLog = new InventoryLog();
                        transferCancelLog.setWarehouseId(order.getTargetWarehouseId());
                        transferCancelLog.setProductId(item.getProductId());
                        transferCancelLog.setChangeQty(-restoreQty);
                        transferCancelLog.setBeforeQty(tgtBefore);
                        transferCancelLog.setAfterQty(tgtBefore - restoreQty);
                        transferCancelLog.setType("TRANSFER_CANCEL");
                        transferCancelLog.setRefOrderId(orderId);
                        inventoryLogMapper.insert(transferCancelLog);
                    }
                }
            }
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
