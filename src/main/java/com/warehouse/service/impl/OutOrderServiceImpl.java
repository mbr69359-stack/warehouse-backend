package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class OutOrderServiceImpl implements OutOrderService {

    private final OutOrderMapper outOrderMapper;
    private final OutOrderItemMapper outOrderItemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private static final AtomicInteger SEQ = new AtomicInteger(0);

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
    public void confirm(Long orderId, Long operatorId) {
        OutOrder order = outOrderMapper.selectById(orderId);
        if (order == null) throw new RuntimeException("出库单不存在");
        if (!"DRAFT".equals(order.getStatus())) throw new RuntimeException("该出库单已确认");
        List<OutOrderItem> items = outOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutOrderItem>().eq(OutOrderItem::getOrderId, orderId));
        for (OutOrderItem item : items) {
            int qty = item.getQty();
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

    private String generateNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("OUT%s%06d", date, SEQ.incrementAndGet() % 1000000);
    }
}
