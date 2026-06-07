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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final CustomerReturnMapper customerReturnMapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;

    @Override
    public OutOrder getById(Long id) {
        OutOrder order = outOrderMapper.selectById(id);
        if (order == null) throw new BusinessException("出库单不存在");
        // 填充客户名称（忽略软删，保证历史订单仍能显示已删客户名）
        if (order.getCustomerId() != null) {
            Customer c = customerMapper.selectByIdIgnoreDeleted(order.getCustomerId());
            if (c != null) order.setCustomerName(c.getName());
        }
        return order;
    }

    @Override
    public Page<OutOrder> page(int current, int size, String status, Long warehouseId, String startDate, String endDate) {
        LocalDateTime start = startDate != null ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = endDate != null ? LocalDate.parse(endDate).atTime(23, 59, 59) : null;
        LambdaQueryWrapper<OutOrder> q = new LambdaQueryWrapper<OutOrder>()
                .eq(status != null, OutOrder::getStatus, status)
                .eq(warehouseId != null, OutOrder::getWarehouseId, warehouseId)
                .ge(start != null, OutOrder::getCreateTime, start)
                .le(end != null, OutOrder::getCreateTime, end)
                .orderByDesc(OutOrder::getCreateTime);
        Page<OutOrder> result = outOrderMapper.selectPage(new Page<>(current, size), q);
        // 批量填充客户名称，避免 N+1 查询
        List<Long> cids = result.getRecords().stream()
                .filter(o -> o.getCustomerId() != null)
                .map(OutOrder::getCustomerId).distinct()
                .collect(java.util.stream.Collectors.toList());
        if (!cids.isEmpty()) {
            // 忽略软删，保证历史订单仍能显示已删客户名
            java.util.Map<Long, String> nameMap = customerMapper.selectByIdsIgnoreDeleted(cids)
                    .stream().collect(java.util.stream.Collectors.toMap(
                            Customer::getId,
                            Customer::getName));
            result.getRecords().forEach(o -> {
                if (o.getCustomerId() != null) o.setCustomerName(nameMap.get(o.getCustomerId()));
            });
        }
        return result;
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
        // 保存客户关联（可选字段）
        order.setCustomerId(dto.getCustomerId());
        outOrderMapper.insert(order);

        if ("DAMAGE_OUT".equals(dto.getType())
                && dto.getDamageRecordIds() != null
                && !dto.getDamageRecordIds().isEmpty()) {
            List<DamageRecord> damages = damageRecordMapper.selectBatchIds(dto.getDamageRecordIds());
            for (DamageRecord d : damages) {
                if (!Objects.equals(dto.getWarehouseId(), d.getWarehouseId())) {
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
                UpdateWrapper<DamageRecord> lock = new UpdateWrapper<>();
                // Bug 4 fix: 同时检查 status=PENDING，防止已核销的记录被重复关联出库
                lock.eq("id", d.getId()).isNull("out_order_id").eq("status", "PENDING")
                        .set("out_order_id", order.getId());
                int updated = damageRecordMapper.update(null, lock);
                if (updated == 0) {
                    throw new BusinessException("损坏记录 " + d.getId() + " 已被其他出库单占用或已核销，请刷新后重试");
                }
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
        OutOrder order = outOrderMapper.selectByIdForUpdate(orderId);
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
            // actualQty 为 null 或 0 时用计划数量兜底，防止补发出库不扣库存
            int qty = (item.getActualQty() != null && item.getActualQty() > 0) ? item.getActualQty()
                    : (item.getQty() != null ? item.getQty() : 0);
            if (qty <= 0) continue;

            StockSnapshot snap = snapshotMapper.selectOneForUpdate(item.getProductId(), order.getWarehouseId());
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
            if ("DAMAGE_OUT".equals(order.getType()) || "REPLACEMENT_OUT".equals(order.getType())) {
                com.warehouse.entity.Product p = productMapper.selectById(item.getProductId());
                if (p != null && p.getCostPrice() != null) {
                    BigDecimal loss = p.getCostPrice().multiply(BigDecimal.valueOf(qty));
                    outEntry.setNote("单位成本:" + p.getCostPrice().toPlainString() + ",损失金额:" + loss.toPlainString());
                }
            }
            ledgerMapper.insert(outEntry);

            snapshotMapper.upsert(item.getProductId(), order.getWarehouseId(),
                    afterQty, snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0);

            if ("TRANSFER".equals(order.getType()) && order.getTargetWarehouseId() != null) {
                StockSnapshot targetSnap = snapshotMapper.selectOneForUpdate(item.getProductId(), order.getTargetWarehouseId());
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

        // Bug 7 fix: 所有绑定该出库单的损坏记录统一标为 RESOLVED，不再跳过 qty=0 的商品
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
        return outOrderItemMapper.selectItemsWithProductName(orderId);
    }

    @Override
    @Transactional
    public void delete(Long orderId, Long operatorId) {
        OutOrder order = outOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw new BusinessException("出库单不存在");

        if ("CONFIRMED".equals(order.getStatus())) {
            List<OutOrderItem> items = outOrderItemMapper.selectList(
                    new LambdaQueryWrapper<OutOrderItem>().eq(OutOrderItem::getOrderId, orderId));
            for (OutOrderItem item : items) {
                int restoreQty = (item.getActualQty() != null) ? item.getActualQty()
                        : (item.getQty() != null ? item.getQty() : 0);
                if (restoreQty <= 0) continue;

                StockSnapshot srcSnap = snapshotMapper.selectOneForUpdate(item.getProductId(), order.getWarehouseId());
                BigDecimal srcBefore = srcSnap != null ? srcSnap.getCurrentQty() : BigDecimal.ZERO;
                BigDecimal srcAfter  = srcBefore.add(BigDecimal.valueOf(restoreQty));

                InventoryLedger cancelEntry = new InventoryLedger();
                cancelEntry.setId(UUID.randomUUID().toString());
                cancelEntry.setProductId(item.getProductId());
                cancelEntry.setLocationId(order.getWarehouseId());
                cancelEntry.setChangeQty(new BigDecimal(restoreQty));
                cancelEntry.setType("outbound_cancel");
                cancelEntry.setDocumentNo(order.getOrderNo());
                // Bug 9 fix: 记录真实操作人
                cancelEntry.setOperator(operatorId != null ? String.valueOf(operatorId) : "system");
                cancelEntry.setNote("撤销出库单 " + order.getOrderNo());
                cancelEntry.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
                cancelEntry.setSynced(1);
                ledgerMapper.insert(cancelEntry);

                snapshotMapper.upsert(item.getProductId(), order.getWarehouseId(),
                        srcAfter,
                        srcSnap != null && srcSnap.getAlertQty() != null ? srcSnap.getAlertQty() : 0);

                if ("TRANSFER".equals(order.getType()) && order.getTargetWarehouseId() != null) {
                    StockSnapshot tgtSnap = snapshotMapper.selectOneForUpdate(item.getProductId(), order.getTargetWarehouseId());
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
                    transferCancelEntry.setOperator(operatorId != null ? String.valueOf(operatorId) : "system");
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

            if ("REPLACEMENT_OUT".equals(order.getType())) {
                UpdateWrapper<CustomerReturn> uw = new UpdateWrapper<>();
                uw.eq("out_order_id", orderId).set("status", "INBOUND_DONE").set("out_order_id", null);
                customerReturnMapper.update(null, uw);
            }
        } else {
            // Bug 8 fix: DRAFT 状态的出库单被删时也要清理关联引用
            if ("DAMAGE_OUT".equals(order.getType())) {
                UpdateWrapper<DamageRecord> uw = new UpdateWrapper<>();
                uw.eq("out_order_id", orderId).set("out_order_id", null);
                damageRecordMapper.update(null, uw);
            }
            if ("REPLACEMENT_OUT".equals(order.getType())) {
                // 清空退换货单的补发出库单引用，防止后续流程因引用悬空而报错
                UpdateWrapper<CustomerReturn> uw = new UpdateWrapper<>();
                uw.eq("out_order_id", orderId).set("out_order_id", null);
                customerReturnMapper.update(null, uw);
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