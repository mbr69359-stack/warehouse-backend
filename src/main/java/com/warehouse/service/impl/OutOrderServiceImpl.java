package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.OutOrderDTO;
import com.warehouse.entity.*;
import com.warehouse.mapper.*;
import com.warehouse.service.SysConfigService;
import com.warehouse.common.BusinessException;
import com.warehouse.service.OutOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
    private final ExpenseMapper expenseMapper;
    private final SysConfigService sysConfigService;

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
        order.setCustomerId(dto.getCustomerId());
        if ("SALE".equals(dto.getType())) {
            if (dto.getSaleChannel() == null || dto.getSaleChannel().trim().isEmpty()) {
                throw new BusinessException("销售出库必须选择销售渠道（零售/批发）");
            }
            if (!"RETAIL".equals(dto.getSaleChannel()) && !"WHOLESALE".equals(dto.getSaleChannel())) {
                throw new BusinessException("销售渠道值无效，只允许 RETAIL 或 WHOLESALE");
            }
            order.setSaleChannel(dto.getSaleChannel());
        }
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
                com.warehouse.entity.Product dp = productMapper.selectById(entry.getKey());
                item.setCostPrice(dp != null && dp.getCostPrice() != null ? dp.getCostPrice() : BigDecimal.ZERO);
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
                com.warehouse.entity.Product prod = productMapper.selectById(i.getProductId());
                item.setCostPrice(prod != null && prod.getCostPrice() != null ? prod.getCostPrice() : BigDecimal.ZERO);
                outOrderItemMapper.insert(item);
            }
        }
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId, List<ConfirmItemDTO> actualItems, Long operatorId) {
        LocalDateTime confirmTime = LocalDateTime.now();
        int confirmed = outOrderMapper.markConfirmedFromDraft(orderId, confirmTime);
        if (confirmed == 0) throw new BusinessException("该出库单已确认或已作废");
        OutOrder order = outOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("出库单不存在");

        if ("TRANSFER".equals(order.getType())) {
            if (order.getTargetWarehouseId() == null) throw new BusinessException("调拨单缺少目标仓库");
            Warehouse targetWarehouse = warehouseMapper.selectById(order.getTargetWarehouseId());
            if (targetWarehouse == null) throw new BusinessException("目标仓库不存在或已删除");
            if (Integer.valueOf(0).equals(targetWarehouse.getStatus())) throw new BusinessException("目标仓库已禁用，无法入库");
        }

        String warehouseType = warehouseMapper.selectTypeById(order.getWarehouseId());
        boolean isBoxWarehouse = "BOX".equals(warehouseType);

        List<OutOrderItem> items = outOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutOrderItem>().eq(OutOrderItem::getOrderId, orderId));

        java.util.Map<Long, Integer> actualQtyMap = new java.util.HashMap<>();
        if (actualItems != null && !actualItems.isEmpty()) {
            for (ConfirmItemDTO c : actualItems) {
                if (c.getItemId() != null && c.getActualQty() != null) {
                    actualQtyMap.put(c.getItemId(), c.getActualQty());
                }
            }
        }

        // BUG-1 fix: 损坏出库不允许部分核销，actualQty 必须等于计划数量（或填 0 整条跳过）
        if ("DAMAGE_OUT".equals(order.getType())) {
            for (OutOrderItem item : items) {
                int planned = item.getQty() != null ? item.getQty() : 0;
                int actual  = actualQtyMap.containsKey(item.getId()) ? actualQtyMap.get(item.getId()) : planned;
                if (actual != 0 && actual != planned) {
                    throw new BusinessException("损坏出库不支持部分核销，商品ID " + item.getProductId()
                            + " 计划 " + planned + " 件，实际填写 " + actual + " 件。"
                            + "如需调整，请删除本单重新按实际损坏数量建记录。");
                }
            }
        }

        String ledgerType;
        if ("TRANSFER".equals(order.getType())) ledgerType = "transfer_out";
        else if ("DAMAGE_OUT".equals(order.getType())) ledgerType = "damage_out";
        else if ("REPLACEMENT_OUT".equals(order.getType())) ledgerType = "replacement_out";
        else ledgerType = "outbound";

        java.util.Set<Long> writtenOffProductIds = new java.util.HashSet<>();
        int totalPieceQtyForCommission = 0;
        for (OutOrderItem item : items) {
            int qty = actualQtyMap.containsKey(item.getId()) ? actualQtyMap.get(item.getId())
                    : (item.getQty() != null ? item.getQty() : 0);
            item.setActualQty(qty);
            if (qty <= 0) {
                outOrderItemMapper.updateById(item);
                continue;
            }

            writtenOffProductIds.add(item.getProductId());
            // BUG-2 fix: 确认出库时重新读取最新成本价，避免草稿期间成本变化导致毛利偏差
            com.warehouse.entity.Product latestProd = productMapper.selectById(item.getProductId());
            if (latestProd != null && latestProd.getCostPrice() != null) {
                item.setCostPrice(latestProd.getCostPrice());
            }
            outOrderItemMapper.updateById(item);

            // BOX仓库：箱数换算为个数（DAMAGE_OUT/REPLACEMENT_OUT的qty已是个数，不换算）
            boolean hasPrBox = latestProd != null && latestProd.getQtyPerBox() != null && latestProd.getQtyPerBox() > 0;
            boolean qtyIsPiece = "DAMAGE_OUT".equals(order.getType()) || "REPLACEMENT_OUT".equals(order.getType());
            int pieceQty = (isBoxWarehouse && hasPrBox && !qtyIsPiece)
                    ? qty * latestProd.getQtyPerBox() : qty;

            if ("DAMAGE_OUT".equals(order.getType())) {
                // Bug #4 fix: 库存已在损坏记录 create() 时扣减，DAMAGE_OUT 确认只标记核销，不重复操作快照
                continue;
            }

            totalPieceQtyForCommission += pieceQty;

            StockSnapshot snap = snapshotMapper.selectOneForUpdate(item.getProductId(), order.getWarehouseId());
            BigDecimal beforeQty = snap != null ? snap.getCurrentQty() : BigDecimal.ZERO;
            if (beforeQty.compareTo(BigDecimal.valueOf(pieceQty)) < 0) {
                throw new BusinessException("商品ID " + item.getProductId() + " 库存不足，当前：" +
                        beforeQty.toPlainString() + "，需要：" + pieceQty);
            }
            BigDecimal afterQty = beforeQty.subtract(BigDecimal.valueOf(pieceQty));

            InventoryLedger outEntry = new InventoryLedger();
            outEntry.setId(UUID.randomUUID().toString());
            outEntry.setProductId(item.getProductId());
            outEntry.setLocationId(order.getWarehouseId());
            outEntry.setChangeQty(new BigDecimal(-pieceQty));
            outEntry.setType(ledgerType);
            outEntry.setDocumentNo(order.getOrderNo());
            outEntry.setOperator(String.valueOf(operatorId));
            outEntry.setOccurredAt(LocalDateTime.now());
            outEntry.setSynced(1);
            if ("REPLACEMENT_OUT".equals(order.getType())) {
                if (latestProd != null && latestProd.getCostPrice() != null) {
                    BigDecimal loss = latestProd.getCostPrice().multiply(BigDecimal.valueOf(pieceQty));
                    outEntry.setNote("单位成本:" + latestProd.getCostPrice().toPlainString() + ",损失金额:" + loss.toPlainString());
                }
            }
            ledgerMapper.insert(outEntry);

            snapshotMapper.upsert(item.getProductId(), order.getWarehouseId(),
                    afterQty, snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0);

            if ("TRANSFER".equals(order.getType()) && order.getTargetWarehouseId() != null) {
                StockSnapshot targetSnap = snapshotMapper.selectOneForUpdate(item.getProductId(), order.getTargetWarehouseId());
                BigDecimal targetBefore = targetSnap != null ? targetSnap.getCurrentQty() : BigDecimal.ZERO;
                BigDecimal targetAfter = targetBefore.add(BigDecimal.valueOf(pieceQty));

                InventoryLedger inEntry = new InventoryLedger();
                inEntry.setId(UUID.randomUUID().toString());
                inEntry.setProductId(item.getProductId());
                inEntry.setLocationId(order.getTargetWarehouseId());
                inEntry.setChangeQty(new BigDecimal(pieceQty));
                inEntry.setType("transfer_in");
                inEntry.setDocumentNo(order.getOrderNo());
                inEntry.setOperator(String.valueOf(operatorId));
                inEntry.setNote("调拨自仓库 " + order.getWarehouseId());
                inEntry.setOccurredAt(LocalDateTime.now());
                inEntry.setSynced(1);
                ledgerMapper.insert(inEntry);

                snapshotMapper.upsert(item.getProductId(), order.getTargetWarehouseId(),
                        targetAfter,
                        targetSnap != null && targetSnap.getAlertQty() != null ? targetSnap.getAlertQty() : 0);
            }
        }

        // 只把实际出库的商品对应损坏记录标为 RESOLVED；
        // actualQty=0 跳过的商品，解除与本单的绑定，回到 PENDING 等待下次处理
        if ("DAMAGE_OUT".equals(order.getType())) {
            LocalDateTime resolvedAt = LocalDateTime.now();
            List<DamageRecord> damages = damageRecordMapper.selectList(
                    new LambdaQueryWrapper<DamageRecord>().eq(DamageRecord::getOutOrderId, orderId));
            for (DamageRecord d : damages) {
                if (writtenOffProductIds.contains(d.getProductId())) {
                    d.setStatus("RESOLVED");
                    d.setResolvedAt(resolvedAt);
                } else {
                    d.setOutOrderId(null);
                }
                damageRecordMapper.updateById(d);
            }
        }

        if ("SALE".equals(order.getType()) && order.getSaleChannel() != null) {
            autoCreateCommission(order, totalPieceQtyForCommission, operatorId);
        }

        order.setStatus("CONFIRMED");
        order.setConfirmTime(confirmTime);
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
        if ("VOIDED".equals(order.getStatus())) throw new BusinessException("该单据已作废，不能重复操作");

        if ("CONFIRMED".equals(order.getStatus())) {
            // Bug #4 fix: DAMAGE_OUT 确认时不写流水（库存在破损登记时已扣减），
            // 作废也不还原库存——否则已损坏货物会重新出现在可用库存中
            if (!"DAMAGE_OUT".equals(order.getType())) {
                // 按原始流水冲销：当初每条流水扣多少就反向冲多少，
                // 不用当前每箱个数反推——确认后若改过每箱个数，反推会还原错数
                List<InventoryLedger> originals = ledgerMapper.selectList(
                        new LambdaQueryWrapper<InventoryLedger>()
                                .eq(InventoryLedger::getDocumentNo, order.getOrderNo())
                                .in(InventoryLedger::getType,
                                        "outbound", "transfer_out", "replacement_out", "transfer_in"));
                List<InventoryLedger> sourceEntries = new java.util.ArrayList<>();
                java.util.Map<Long, java.util.Deque<InventoryLedger>> targetByProduct = new java.util.HashMap<>();
                for (InventoryLedger original : originals) {
                    if ("transfer_in".equals(original.getType())) {
                        targetByProduct.computeIfAbsent(original.getProductId(),
                                k -> new java.util.ArrayDeque<>()).add(original);
                    } else {
                        sourceEntries.add(original);
                    }
                }

                // Bug 9 fix: 记录真实操作人
                String operator = operatorId != null ? String.valueOf(operatorId) : "system";
                for (InventoryLedger src : sourceEntries) {
                    BigDecimal restoreQty = src.getChangeQty().negate(); // 原扣减为负数，冲销为正

                    // 死锁修复：与 confirm() 保持一致的加锁顺序（先锁源仓快照，再锁目标仓快照），
                    // 避免 confirm 与 delete 并发时 AB-BA 互等死锁；两把锁都拿到、校验通过后才开始写入
                    StockSnapshot srcSnap = snapshotMapper.selectOneForUpdate(src.getProductId(), src.getLocationId());

                    InventoryLedger tgt = null;
                    StockSnapshot tgtSnap = null;
                    BigDecimal tgtBefore = BigDecimal.ZERO;
                    java.util.Deque<InventoryLedger> targetQueue = targetByProduct.get(src.getProductId());
                    if (targetQueue != null) tgt = targetQueue.poll();
                    if (tgt != null) {
                        tgtSnap = snapshotMapper.selectOneForUpdate(tgt.getProductId(), tgt.getLocationId());
                        tgtBefore = tgtSnap != null ? tgtSnap.getCurrentQty() : BigDecimal.ZERO;
                        if (tgtBefore.compareTo(tgt.getChangeQty()) < 0)
                            throw new BusinessException("该单货物已被使用，不能删除，请先盘点");
                    }

                    BigDecimal srcBefore = srcSnap != null ? srcSnap.getCurrentQty() : BigDecimal.ZERO;

                    InventoryLedger cancelEntry = new InventoryLedger();
                    cancelEntry.setId(UUID.randomUUID().toString());
                    cancelEntry.setProductId(src.getProductId());
                    cancelEntry.setLocationId(src.getLocationId());
                    cancelEntry.setChangeQty(restoreQty);
                    cancelEntry.setType("outbound_cancel");
                    cancelEntry.setDocumentNo(order.getOrderNo());
                    cancelEntry.setOperator(operator);
                    cancelEntry.setQtyUnit(src.getQtyUnit());
                    cancelEntry.setNote("作废出库单 " + order.getOrderNo());
                    cancelEntry.setOccurredAt(LocalDateTime.now());
                    cancelEntry.setSynced(1);
                    ledgerMapper.insert(cancelEntry);

                    snapshotMapper.upsert(src.getProductId(), src.getLocationId(),
                            srcBefore.add(restoreQty),
                            srcSnap != null && srcSnap.getAlertQty() != null ? srcSnap.getAlertQty() : 0);

                    if (tgt != null) {
                        InventoryLedger transferCancelEntry = new InventoryLedger();
                        transferCancelEntry.setId(UUID.randomUUID().toString());
                        transferCancelEntry.setProductId(tgt.getProductId());
                        transferCancelEntry.setLocationId(tgt.getLocationId());
                        transferCancelEntry.setChangeQty(tgt.getChangeQty().negate());
                        transferCancelEntry.setType("transfer_cancel");
                        transferCancelEntry.setDocumentNo(order.getOrderNo());
                        transferCancelEntry.setOperator(operator);
                        transferCancelEntry.setQtyUnit(tgt.getQtyUnit());
                        transferCancelEntry.setNote("作废调拨，还原至仓库 " + order.getWarehouseId());
                        transferCancelEntry.setOccurredAt(LocalDateTime.now());
                        transferCancelEntry.setSynced(1);
                        ledgerMapper.insert(transferCancelEntry);

                        snapshotMapper.upsert(tgt.getProductId(), tgt.getLocationId(),
                                tgtBefore.subtract(tgt.getChangeQty()),
                                tgtSnap != null && tgtSnap.getAlertQty() != null ? tgtSnap.getAlertQty() : 0);
                    }
                }
            }

            if ("SALE".equals(order.getType())) {
                handleCommissionOnDelete(order, operatorId);
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

            // 已确认单作废而非删除：单据与明细保留，状态置为 VOIDED 供"已作废"页签查询
            order.setStatus("VOIDED");
            outOrderMapper.updateById(order);
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

            outOrderItemMapper.delete(new LambdaQueryWrapper<OutOrderItem>().eq(OutOrderItem::getOrderId, orderId));
            outOrderMapper.deleteById(orderId);
        }
    }

    private void autoCreateCommission(OutOrder order, int totalPieceQty, Long operatorId) {
        if (totalPieceQty <= 0) return;

        boolean isRetail = "RETAIL".equals(order.getSaleChannel());
        String configKey = isRetail ? "COMMISSION_RATE_RETAIL" : "COMMISSION_RATE_WHOLESALE";
        BigDecimal defaultRate = isRetail ? BigDecimal.ONE : new BigDecimal("2.00");
        BigDecimal rate = sysConfigService.getDecimal(configKey, defaultRate);

        String channelLabel = isRetail ? "零售" : "批发";
        Expense expense = new Expense();
        expense.setExpenseDate(LocalDate.now());
        expense.setWarehouseId(order.getWarehouseId());
        expense.setType("COMMISSION");
        expense.setAmount(rate.multiply(BigDecimal.valueOf(totalPieceQty)));
        expense.setNote("自动计算：" + order.getOrderNo() + " " + channelLabel + "提成");
        expense.setOperatorId(operatorId);
        expenseMapper.insert(expense);
    }

    private void handleCommissionOnDelete(OutOrder order, Long operatorId) {
        LambdaQueryWrapper<Expense> q = new LambdaQueryWrapper<Expense>()
                .eq(Expense::getType, "COMMISSION")
                .like(Expense::getNote, order.getOrderNo())
                .eq(Expense::getDeleted, 0);
        Expense commission = expenseMapper.selectOne(q);
        if (commission == null) return;

        YearMonth commissionMonth = YearMonth.from(commission.getExpenseDate());
        YearMonth currentMonth    = YearMonth.now();
        if (commissionMonth.equals(currentMonth)) {
            expenseMapper.deleteById(commission.getId());
        } else {
            boolean isRetail = "RETAIL".equals(order.getSaleChannel());
            String channelLabel = isRetail ? "零售" : "批发";
            Expense reversal = new Expense();
            reversal.setExpenseDate(LocalDate.now());
            reversal.setWarehouseId(order.getWarehouseId());
            reversal.setType("COMMISSION");
            reversal.setAmount(commission.getAmount().negate());
            reversal.setNote("冲销：" + order.getOrderNo() + " " + channelLabel + "提成");
            reversal.setOperatorId(operatorId);
            expenseMapper.insert(reversal);
        }
    }

    private String generateNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int rand = ThreadLocalRandom.current().nextInt(100, 1000);
        return String.format("OUT%s%d", ts, rand);
    }
}
