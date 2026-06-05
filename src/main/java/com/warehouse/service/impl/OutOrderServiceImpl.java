package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OutOrderServiceImpl implements OutOrderService {

    private final OutOrderMapper outOrderMapper;
    private final OutOrderItemMapper outOrderItemMapper;
    private final InventoryLedgerMapper ledgerMapper;
    private final StockSnapshotMapper snapshotMapper;
    private final WarehouseMapper warehouseMapper;
    private final DamageRecordMapper damageRecordMapper;

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

        if ("DAMAGE_OUT".equals(dto.getType())
                && dto.getDamageRecordIds() != null
                && !dto.getDamageRecordIds().isEmpty()) {
            List<DamageRecord> damages = damageRecordMapper.selectBatchIds(dto.getDamageRecordIds());
            for (DamageRecord d : damages) {
                if (!dto.getWarehouseId().equals(d.getWarehouseId())) {
                    throw new BusinessException("损坏记录 " + d.getId() + " 不属于所选仓库，无法操作");
                }
            }
            Map<Long, Integer> merged = new LinkedHashMap<>();
            for (DamageRecord d : damages) {
                merged.merge(d.getProductId(), d.getQty(), Integer::sum);
            }
            for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
                OutOrderItem item = new OutOrderItem();
                item.setOrderId(order.getId());
                item.setProductId(entry.getKey());
                item.setQty(entry.getValue());
                item.setPrice(BigDecimal.ZERO);
                outOrderItemMapper.insert(item);
            }
            for (DamageRecord d : damages) {
                d.setOutOrderId(order.getId());
                damageRecordMapper.updateById(d);
            }
        } else {
            if (dto.getItems() == null || dto.getItems().isEmpty()) {
                throw new BusinessException("出库明细不能为空");
            }
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

        String ledgerType;
        if ("TRANSFER".equals(order.getType())) ledgerType = "transfer";
        else if ("DAMAGE_OUT".equals(order.getType())) ledgerType = "damage_out";
        else if ("REPLACEMENT_OUT".equals(order.getType())) ledgerType = "replacement_out";
        else ledgerType = "outbound";

        for (OutOrderItem item : items) {
            int qty = item.getActualQty() != null ? item.getActualQty() : 0;
            if (qty <= 0) continue;

            StockSnapshot snap = snapshotMapper.selectOne(item.getProductId(), order.getWarehouseId());
            BigDecimal beforeQty = snap != null ? snap.getCurrentQty() : BigDecimal.ZERO;
            if (beforeQty.compareTo(BigDecimal.valueOf(qty)) < 0) {
                throw new BusinessException("商品ID " + item.getProductId() + " 库存不足，当前：" +
                        beforeQty.toPlainString() + "，需要：" + qty);
            }
            BigDecimal afterQty = beforeQty.subtract(BigDecimal.valueOf(qty));

            InventoryLedger outEntry = new InventoryLedger();
            outEntry.setId(UUID.randomUUID().toString());
            outEntry.setProductId(item.getProductId());
            outEntry.setLocationId(order.getWarehouseId());
            outEntry.setChangeQty(new BigDecimal(-qty));
            outEntry.setType(ledgerType);
            outEntry.setDocumentNo(order.getOrderNo());
            outEntry.setOperator(String.valueOf(operatorId));
            outEntry.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
            outEntry.setSynced(1);
            ledgerMapper.insert(outEntry);

            snapshotMapper.upsert(item.getProductId(), order.getWarehouseId(),
                    afterQty, snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0);

            if ("TRANSFER".equals(order.getType()) && order.getTargetWarehouseId() != null) {
                StockSnapshot targetSnap = snapshotMapper.selectOne(item.getProductId(), order.getTargetWarehouseId());
                BigDecimal targetBefore = targetSnap != null ? targetSnap.getCurrentQty() : BigDecimal.ZERO;
                BigDecimal targetAfter = targetBefore.add(BigDecimal.valueOf(qty));

                InventoryLedger inEntry = new InventoryLedger();
                inEntry.setId(UUID.randomUUID().toString());
                inEntry.setProductId(item.getProductId());
                inEntry.setLocationId(order.getTargetWarehouseId());
                inEntry.setChangeQty(new BigDecimal(qty));
                inEntry.setType("transfer_in");
                inEntry.setDocumentNo(order.getOrderNo());
                inEntry.setOperator(String.valueOf(operatorId));
                inEntry.setNote("调拨自仓库 " + order.getWarehouseId());
                inEntry.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
                inEntry.setSynced(1);
                ledgerMapper.insert(inEntry);

                snapshotMapper.upsert(item.getProductId(), order.getTargetWarehouseId(),
                        targetAfter,
                        targetSnap != null && targetSnap.getAlertQty() != null ? targetSnap.getAlertQty() : 0);
            }
        }

        if ("DAMAGE_OUT".equals(order.getType())) {
            LocalDateTime resolvedAt = LocalDateTime.now(ZoneOffset.UTC);
            List<DamageRecord> damages = damageRecordMapper.selectList(
                    new LambdaQueryWrapper<DamageRecord>().eq(DamageRecord::getOutOrderId, orderId));
            for (DamageRecord d : damages) {
                d.setStatus("RESOLVED");
                d.setResolvedAt(resolvedAt);
                damageRecordMapper.updateById(d);
            }
        }

        order.setStatus("CONFIRMED");
        order.setConfirmTime(LocalDateTime.now(ZoneOffset.UTC));
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
                if (restoreQty <= 0) continue;

                StockSnapshot srcSnap = snapshotMapper.selectOne(item.getProductId(), order.getWarehouseId());
                BigDecimal srcBefore = srcSnap != null ? srcSnap.getCurrentQty() : BigDecimal.ZERO;
                BigDecimal srcAfter  = srcBefore.add(BigDecimal.valueOf(restoreQty));

                InventoryLedger cancelEntry = new InventoryLedger();
                cancelEntry.setId(UUID.randomUUID().toString());
                cancelEntry.setProductId(item.getProductId());
                cancelEntry.setLocationId(order.getWarehouseId());
                cancelEntry.setChangeQty(new BigDecimal(restoreQty));
                cancelEntry.setType("outbound_cancel");
                cancelEntry.setDocumentNo(order.getOrderNo());
                cancelEntry.setOperator("system");
                cancelEntry.setNote("撤销出库单 " + order.getOrderNo());
                cancelEntry.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
                cancelEntry.setSynced(1);
                ledgerMapper.insert(cancelEntry);

                snapshotMapper.upsert(item.getProductId(), order.getWarehouseId(),
                        srcAfter,
                        srcSnap != null && srcSnap.getAlertQty() != null ? srcSnap.getAlertQty() : 0);

                if ("TRANSFER".equals(order.getType()) && order.getTargetWarehouseId() != null) {
                    StockSnapshot tgtSnap = snapshotMapper.selectOne(item.getProductId(), order.getTargetWarehouseId());
                    BigDecimal tgtBefore = tgtSnap != null ? tgtSnap.getCurrentQty() : BigDecimal.ZERO;
                    BigDecimal restoreQtyBD = BigDecimal.valueOf(restoreQty);
                    if (tgtBefore.compareTo(restoreQtyBD) < 0)
                        throw new BusinessException("目标仓库库存不足以撤销调拨，当前：" + tgtBefore + "，需撤回：" + restoreQty);

                    BigDecimal tgtAfter = tgtBefore.subtract(restoreQtyBD);

                    InventoryLedger transferCancelEntry = new InventoryLedger();
                    transferCancelEntry.setId(UUID.randomUUID().toString());
                    transferCancelEntry.setProductId(item.getProductId());
                    transferCancelEntry.setLocationId(order.getTargetWarehouseId());
                    transferCancelEntry.setChangeQty(new BigDecimal(-restoreQty));
                    transferCancelEntry.setType("transfer_cancel");
                    transferCancelEntry.setDocumentNo(order.getOrderNo());
                    transferCancelEntry.setOperator("system");
                    transferCancelEntry.setNote("撤销调拨，还原至仓库 " + order.getWarehouseId());
                    transferCancelEntry.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
                    transferCancelEntry.setSynced(1);
                    ledgerMapper.insert(transferCancelEntry);

                    snapshotMapper.upsert(item.getProductId(), order.getTargetWarehouseId(),
                            tgtAfter,
                            tgtSnap != null && tgtSnap.getAlertQty() != null ? tgtSnap.getAlertQty() : 0);
                }
            }

            if ("DAMAGE_OUT".equals(order.getType())) {
                UpdateWrapper<DamageRecord> uw = new UpdateWrapper<>();
                uw.eq("out_order_id", orderId)
                  .set("status", "PENDING")
                  .set("resolved_at", null)
                  .set("out_order_id", null);
                damageRecordMapper.update(null, uw);
            }
        } else if ("DAMAGE_OUT".equals(order.getType())) {
            UpdateWrapper<DamageRecord> uw = new UpdateWrapper<>();
            uw.eq("out_order_id", orderId).set("out_order_id", null);
            damageRecordMapper.update(null, uw);
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
