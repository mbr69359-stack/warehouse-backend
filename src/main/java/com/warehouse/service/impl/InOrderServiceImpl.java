package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.InOrderDTO;
import com.warehouse.entity.*;
import com.warehouse.mapper.*;
import java.math.RoundingMode;
import com.warehouse.common.BusinessException;
import com.warehouse.service.InOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final InventoryLedgerMapper ledgerMapper;
    private final StockSnapshotMapper snapshotMapper;
    private final CustomerReturnMapper customerReturnMapper;
    private final DamageRecordMapper damageRecordMapper;
    private final ProductMapper productMapper;
    private final ProductCostHistoryMapper costHistoryMapper;
    private final WarehouseMapper warehouseMapper;

    @Override
    public Page<InOrder> page(int current, int size, String status, Long warehouseId, Long supplierId, String startDate, String endDate) {
        LocalDateTime start = startDate != null ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = endDate != null ? LocalDate.parse(endDate).atTime(23, 59, 59) : null;
        LambdaQueryWrapper<InOrder> q = new LambdaQueryWrapper<InOrder>()
                .eq(status != null, InOrder::getStatus, status)
                .eq(warehouseId != null, InOrder::getWarehouseId, warehouseId)
                .eq(supplierId != null, InOrder::getSupplierId, supplierId)
                .ge(start != null, InOrder::getCreateTime, start)
                .le(end != null, InOrder::getCreateTime, end)
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
                item.setActualQty(i.getActualQty());
                item.setPrice(i.getPrice());
                inOrderItemMapper.insert(item);
            }
        }
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId, List<ConfirmItemDTO> actualItems, Long operatorId) {
        // Bug 1 fix: 加行锁，防止并发重复确认导致库存翻倍
        InOrder order = inOrderMapper.selectByIdForUpdate(orderId);
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

        String warehouseType = warehouseMapper.selectTypeById(order.getWarehouseId());
        boolean isBoxWarehouse = "BOX".equals(warehouseType);

        for (InOrderItem item : items) {
            int rawQty = item.getActualQty() != null ? item.getActualQty()
                    : (item.getPlanQty() != null ? item.getPlanQty() : 0);
            if (rawQty <= 0) continue;

            // BOX仓且已设qtyPerBox：换算为个数；未设：保留箱数并标记BOX单位
            Product productForUnit = productMapper.selectById(item.getProductId());
            boolean hasQtyPerBox = productForUnit != null
                    && productForUnit.getQtyPerBox() != null
                    && productForUnit.getQtyPerBox() > 0;
            int qtyPerBoxVal = hasQtyPerBox ? productForUnit.getQtyPerBox() : 0;

            int qty;
            String ledgerQtyUnit;
            if (isBoxWarehouse) {
                if (hasQtyPerBox) {
                    qty = rawQty * qtyPerBoxVal;
                    ledgerQtyUnit = "PIECE";
                } else {
                    qty = rawQty;
                    ledgerQtyUnit = "BOX";
                }
            } else {
                qty = rawQty;
                ledgerQtyUnit = "PIECE";
            }

            // 单据保留用户填的原始价（BOX仓即每箱价）；成本计算用换算后的每个价
            BigDecimal piecePrice = item.getPrice();
            if (isBoxWarehouse && hasQtyPerBox && qtyPerBoxVal > 1
                    && piecePrice != null
                    && piecePrice.compareTo(BigDecimal.ZERO) > 0) {
                piecePrice = piecePrice.divide(
                        BigDecimal.valueOf(qtyPerBoxVal), 4, RoundingMode.HALF_UP);
            }

            // Bug 2 fix: 加行锁，防止并发写入 snapshot 时数字互相覆盖
            StockSnapshot snap = snapshotMapper.selectOneForUpdate(item.getProductId(), order.getWarehouseId());
            BigDecimal beforeQty = snap != null ? snap.getCurrentQty() : BigDecimal.ZERO;
            BigDecimal afterQty = beforeQty.add(BigDecimal.valueOf(qty));

            InventoryLedger entry = new InventoryLedger();
            entry.setId(UUID.randomUUID().toString());
            entry.setProductId(item.getProductId());
            entry.setLocationId(order.getWarehouseId());
            entry.setChangeQty(new BigDecimal(qty));
            entry.setType("inbound");
            entry.setDocumentNo(order.getOrderNo());
            entry.setOperator(String.valueOf(operatorId));
            entry.setQtyUnit(ledgerQtyUnit);
            entry.setOccurredAt(LocalDateTime.now());
            entry.setSynced(1);

            // 加权平均成本：用全仓库存量做权重（成本价是全产品字段，必须跨仓库汇总）
            if (piecePrice != null && piecePrice.compareTo(BigDecimal.ZERO) > 0) {
                Product prod = productMapper.selectById(item.getProductId());
                if (prod != null) {
                    BigDecimal totalBefore = snapshotMapper.selectTotalQtyByProductId(item.getProductId()); // 入库前全仓合计
                    BigDecimal totalAfter  = totalBefore.add(BigDecimal.valueOf(qty));                       // 入库后全仓合计
                    if (totalAfter.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal oldCost = prod.getCostPrice() != null ? prod.getCostPrice() : BigDecimal.ZERO;
                        BigDecimal newCost = totalBefore.multiply(oldCost)
                                .add(BigDecimal.valueOf(qty).multiply(piecePrice))
                                .divide(totalAfter, 4, RoundingMode.HALF_UP)
                                .setScale(2, RoundingMode.HALF_UP);
                        Product toUpdate = new Product();
                        toUpdate.setId(prod.getId());
                        toUpdate.setCostPrice(newCost);
                        productMapper.updateById(toUpdate);
                        entry.setNote("进价" + piecePrice.toPlainString()
                                + " 均价" + oldCost.setScale(2, RoundingMode.HALF_UP) + "→" + newCost);

                        if (newCost.compareTo(oldCost) != 0) {
                            ProductCostHistory history = new ProductCostHistory();
                            history.setProductId(item.getProductId());
                            history.setOldPrice(oldCost.setScale(2, RoundingMode.HALF_UP));
                            history.setNewPrice(newCost);
                            history.setChangedAt(LocalDateTime.now());
                            history.setOrderNo(order.getOrderNo());
                            history.setQtyAdded(qty);
                            costHistoryMapper.insert(history);
                        }
                    }
                }
            }

            ledgerMapper.insert(entry);

            snapshotMapper.upsert(item.getProductId(), order.getWarehouseId(),
                    afterQty, snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0);
        }

        order.setStatus("CONFIRMED");
        order.setConfirmTime(LocalDateTime.now());
        inOrderMapper.updateById(order);
    }

    @Override
    public InOrder getById(Long id) {
        InOrder order = inOrderMapper.selectById(id);
        if (order == null) throw new BusinessException("入库单不存在");
        return order;
    }

    @Override
    public List<InOrderItem> getItems(Long orderId) {
        return inOrderItemMapper.selectList(
                new LambdaQueryWrapper<InOrderItem>().eq(InOrderItem::getOrderId, orderId));
    }

    @Override
    @Transactional
    public void delete(Long orderId, Long operatorId) {
        InOrder order = inOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw new BusinessException("入库单不存在");

        if ("CONFIRMED".equals(order.getStatus())) {
            // RETURN_IN 的 confirmInbound() 有意跳过了库存增加（退货视为损坏直接核销），
            // 所以删除时也不能扣减——库存从未被加进来过。
            if (!"RETURN_IN".equals(order.getType())) {
                List<InOrderItem> items = inOrderItemMapper.selectList(
                        new LambdaQueryWrapper<InOrderItem>().eq(InOrderItem::getOrderId, orderId));
                // Bug #1 fix: 撤销时需与 confirm() 保持相同的 BOX→个数换算逻辑
                String warehouseType = warehouseMapper.selectTypeById(order.getWarehouseId());
                boolean isBoxWarehouse = "BOX".equals(warehouseType);

                for (InOrderItem item : items) {
                    int rawQty = item.getActualQty() != null ? item.getActualQty()
                            : (item.getPlanQty() != null ? item.getPlanQty() : 0);
                    if (rawQty <= 0) continue;

                    // Bug #1 fix: BOX仓且设了每箱数量时，箱数×每箱数量 = 当初实际入库的个数
                    Product prod = productMapper.selectById(item.getProductId());
                    boolean hasQtyPerBox = prod != null && prod.getQtyPerBox() != null && prod.getQtyPerBox() > 0;
                    int actualQty = (isBoxWarehouse && hasQtyPerBox) ? rawQty * prod.getQtyPerBox() : rawQty;

                    StockSnapshot snap = snapshotMapper.selectOneForUpdate(item.getProductId(), order.getWarehouseId());
                    BigDecimal beforeQty = snap != null ? snap.getCurrentQty() : BigDecimal.ZERO;
                    BigDecimal actualQtyBD = BigDecimal.valueOf(actualQty);

                    // Bug #2 fix: 还原成本价时用全仓库存量做权重（与 confirm() 口径对称）
                    BigDecimal allWarehouseTotalBefore = snapshotMapper.selectTotalQtyByProductId(item.getProductId());

                    if (beforeQty.compareTo(actualQtyBD) < 0)
                        throw new BusinessException("库存不足以撤销入库，当前库存：" + beforeQty + "，需撤回：" + actualQty);

                    BigDecimal afterQty = beforeQty.subtract(actualQtyBD);

                    InventoryLedger entry = new InventoryLedger();
                    entry.setId(UUID.randomUUID().toString());
                    entry.setProductId(item.getProductId());
                    entry.setLocationId(order.getWarehouseId());
                    entry.setChangeQty(new BigDecimal(-actualQty));
                    entry.setType("inbound_cancel");
                    entry.setDocumentNo(order.getOrderNo());
                    entry.setOperator(operatorId != null ? String.valueOf(operatorId) : "system");
                    entry.setNote("撤销入库单 " + order.getOrderNo());
                    entry.setOccurredAt(LocalDateTime.now());
                    entry.setSynced(1);
                    ledgerMapper.insert(entry);

                    snapshotMapper.upsert(item.getProductId(), order.getWarehouseId(),
                            afterQty, snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0);

                    // 还原加权平均成本价：只有原进货有进价才需要还原
                    if (item.getPrice() != null && item.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        if (prod != null && prod.getCostPrice() != null) {
                            BigDecimal totalWith    = allWarehouseTotalBefore;                       // 撤销前全仓合计
                            BigDecimal totalWithout = allWarehouseTotalBefore.subtract(actualQtyBD); // 撤销后全仓合计
                            BigDecimal currentCost  = prod.getCostPrice();
                            if (totalWithout.compareTo(BigDecimal.ZERO) > 0) {
                                // item.price 为单据原始价（BOX仓=每箱价），rawQty×箱价 = 个数×每个价，金额等价
                                BigDecimal revertedCost = currentCost.multiply(totalWith)
                                        .subtract(BigDecimal.valueOf(rawQty).multiply(item.getPrice()))
                                        .divide(totalWithout, 4, RoundingMode.HALF_UP)
                                        .setScale(2, RoundingMode.HALF_UP);
                                if (revertedCost.compareTo(BigDecimal.ZERO) < 0) revertedCost = BigDecimal.ZERO;
                                if (revertedCost.compareTo(currentCost) != 0) {
                                    Product toUpdate = new Product();
                                    toUpdate.setId(prod.getId());
                                    toUpdate.setCostPrice(revertedCost);
                                    productMapper.updateById(toUpdate);
                                    ProductCostHistory history = new ProductCostHistory();
                                    history.setProductId(item.getProductId());
                                    history.setOldPrice(currentCost);
                                    history.setNewPrice(revertedCost);
                                    history.setChangedAt(LocalDateTime.now());
                                    history.setOrderNo(order.getOrderNo());
                                    history.setQtyAdded(-actualQty);
                                    costHistoryMapper.insert(history);
                                }
                            }
                        }
                    }
                }
            }

            // Bug 5 fix: 退货入库单被删时，回退关联的退换货单状态，并清理自动生成的损坏记录
            if ("RETURN_IN".equals(order.getType())) {
                CustomerReturn customerReturn = customerReturnMapper.selectOne(
                        new LambdaQueryWrapper<CustomerReturn>().eq(CustomerReturn::getInOrderId, orderId));
                if (customerReturn != null) {
                    customerReturn.setStatus("DRAFT");
                    customerReturnMapper.updateById(customerReturn);

                    damageRecordMapper.delete(new LambdaQueryWrapper<DamageRecord>()
                            .eq(DamageRecord::getSource, "RETURN_INBOUND")
                            .eq(DamageRecord::getSourceId, customerReturn.getId())
                            .isNull(DamageRecord::getOutOrderId));
                }
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