package com.warehouse.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.common.BusinessException;
import com.warehouse.dto.DamageRecordDTO;
import com.warehouse.dto.DamageTransferDTO;
import com.warehouse.entity.*;
import com.warehouse.mapper.*;
import com.warehouse.service.DamageRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DamageRecordServiceImpl implements DamageRecordService {

    private final DamageRecordMapper damageRecordMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final StockSnapshotMapper snapshotMapper;
    private final InventoryLedgerMapper ledgerMapper;

    @Override
    public Page<DamageRecord> page(int current, int size, String status, Long warehouseId) {
        return damageRecordMapper.selectWithNames(new Page<>(current, size), status, warehouseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DamageRecordDTO dto, String createdBy) {
        // 数量校验：拒绝 null/0/负数，避免无效登记及后续空快照路径的 NPE
        if (dto.getQty() == null || dto.getQty() < 1)
            throw new BusinessException("破损数量必须大于0");

        boolean boxUnit = "BOX".equalsIgnoreCase(dto.getUnit());

        // BOX 仓登记破损前必须先设置每箱数量（与 transfer() 校验口径一致），PIECE 仓不要求
        String warehouseType = warehouseMapper.selectTypeById(dto.getWarehouseId());
        int pieceQty = dto.getQty();
        if ("BOX".equals(warehouseType)) {
            Product product = productMapper.selectById(dto.getProductId());
            if (product == null) throw new BusinessException("商品不存在");
            if (product.getQtyPerBox() == null || product.getQtyPerBox() <= 0)
                throw new BusinessException("请先在商品管理中设置每箱数量");
            // 按箱登记：换算成个数入库；按个登记保持原值
            if (boxUnit) pieceQty = dto.getQty() * product.getQtyPerBox();
        } else if (boxUnit) {
            throw new BusinessException("按个仓库不支持按箱登记损坏");
        }

        // Bug #4 fix: 登记损坏时立即扣减库存，防止已损坏货物再次被销售
        StockSnapshot snap = snapshotMapper.selectOneForUpdate(dto.getProductId(), dto.getWarehouseId());
        BigDecimal currentQty = snap != null ? snap.getCurrentQty() : BigDecimal.ZERO;
        BigDecimal deductQty  = BigDecimal.valueOf(pieceQty);
        if (currentQty.compareTo(deductQty) < 0)
            throw new BusinessException("库存不足，当前库存 " + currentQty.toPlainString() + "，登记损坏数量 " + pieceQty);

        DamageRecord record = new DamageRecord();
        record.setWarehouseId(dto.getWarehouseId());
        record.setProductId(dto.getProductId());
        record.setQty(pieceQty);
        record.setStatus("PENDING");
        record.setRemark(dto.getRemark());
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(createdBy != null ? createdBy : "");
        damageRecordMapper.insert(record);

        String operator = createdBy != null ? createdBy : "";
        ledgerMapper.insert(buildLedger(dto.getProductId(), dto.getWarehouseId(),
                deductQty.negate(), "damage", "DAMAGE-" + record.getId(), operator, LocalDateTime.now()));
        snapshotMapper.upsert(dto.getProductId(), dto.getWarehouseId(),
                currentQty.subtract(deductQty),
                snap.getAlertQty() != null ? snap.getAlertQty() : 0);

        return record.getId();
    }

    @Override
    public long countPendingAvailable(Long warehouseId) {
        return damageRecordMapper.countPendingAvailable(warehouseId);
    }

    @Override
    public List<DamageRecord> listPendingAvailable(Long warehouseId) {
        return damageRecordMapper.selectPendingAvailable(warehouseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DamageRecord record = damageRecordMapper.selectByIdForUpdate(id);
        if (record == null) throw new BusinessException("损坏记录不存在");
        if ("RESOLVED".equals(record.getStatus())) throw new BusinessException("已核销的记录不能删除");

        // Bug #4 fix: 删除时归还之前在 create() 中扣减的库存
        String operator = record.getCreatedBy() != null ? record.getCreatedBy() : "system";
        StockSnapshot snap = snapshotMapper.selectOneForUpdate(record.getProductId(), record.getWarehouseId());
        BigDecimal currentQty = snap != null ? snap.getCurrentQty() : BigDecimal.ZERO;
        BigDecimal restoreQty = BigDecimal.valueOf(record.getQty());
        ledgerMapper.insert(buildLedger(record.getProductId(), record.getWarehouseId(),
                restoreQty, "damage_cancel", "DAMAGE-" + id, operator, LocalDateTime.now()));
        snapshotMapper.upsert(record.getProductId(), record.getWarehouseId(),
                currentQty.add(restoreQty),
                snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0);

        damageRecordMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long damageRecordId, DamageTransferDTO dto, String operator) {
        // 行锁加载破损记录，防并发双重调拨
        DamageRecord record = damageRecordMapper.selectByIdForUpdate(damageRecordId);
        if (record == null) throw new BusinessException("破损记录不存在");
        if (!"PENDING".equals(record.getStatus())) throw new BusinessException("该破损记录已处理");

        Product product = productMapper.selectById(record.getProductId());
        if (product == null) throw new BusinessException("商品不存在");
        if (product.getQtyPerBox() == null || product.getQtyPerBox() <= 0)
            throw new BusinessException("请先在商品管理中设置每箱数量");

        String targetType = warehouseMapper.selectTypeById(dto.getTargetWarehouseId());
        if (!"PIECE".equals(targetType))
            throw new BusinessException("目标仓库必须是按个仓库（PIECE）");

        int damagedQty = record.getQty();
        int qtyPerBox  = product.getQtyPerBox();
        int goodQty    = qtyPerBox - damagedQty;
        if (goodQty < 0)
            throw new BusinessException("破损数(" + damagedQty + ")超过整箱数量(" + qtyPerBox + ")");

        BigDecimal costPrice     = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
        BigDecimal costDeduction = BigDecimal.valueOf(damagedQty).multiply(costPrice);
        String docNo             = "DAMAGE-" + record.getId();
        LocalDateTime now        = LocalDateTime.now();

        // create() 已扣减 damagedQty；此处只将好货（goodQty）从 BOX 调拨到 PIECE，不重复扣坏货
        Long resolvedTransferWarehouseId = null;
        BigDecimal resolvedTransferPrice = null;

        if (goodQty > 0) {
            StockSnapshot boxSnap       = snapshotMapper.selectOneForUpdate(record.getProductId(), record.getWarehouseId());
            BigDecimal    boxCurrentQty = boxSnap != null ? boxSnap.getCurrentQty() : BigDecimal.ZERO;
            if (boxCurrentQty.compareTo(BigDecimal.valueOf(goodQty)) < 0)
                throw new BusinessException("库存不足，BOX 仓剩余 " + boxCurrentQty + " 个，需调拨好货 " + goodQty + " 个");
            snapshotMapper.upsert(record.getProductId(), record.getWarehouseId(),
                    boxCurrentQty.subtract(BigDecimal.valueOf(goodQty)),
                    boxSnap != null && boxSnap.getAlertQty() != null ? boxSnap.getAlertQty() : 0);

            // 流水账：transfer_out(-goodQty) 来自 BOX 仓库
            ledgerMapper.insert(buildLedger(record.getProductId(), record.getWarehouseId(),
                    BigDecimal.valueOf(-goodQty), "transfer_out", docNo, operator, now));

            // 增加 PIECE 仓库快照
            StockSnapshot pieceSnap       = snapshotMapper.selectOneForUpdate(record.getProductId(), dto.getTargetWarehouseId());
            BigDecimal    pieceCurrentQty = pieceSnap != null ? pieceSnap.getCurrentQty() : BigDecimal.ZERO;
            snapshotMapper.upsert(record.getProductId(), dto.getTargetWarehouseId(),
                    pieceCurrentQty.add(BigDecimal.valueOf(goodQty)),
                    pieceSnap != null && pieceSnap.getAlertQty() != null ? pieceSnap.getAlertQty() : 0);

            // 流水账：transfer_in(+goodQty) 到 PIECE 仓库
            ledgerMapper.insert(buildLedger(record.getProductId(), dto.getTargetWarehouseId(),
                    BigDecimal.valueOf(goodQty), "transfer_in", docNo, operator, now));

            resolvedTransferWarehouseId = dto.getTargetWarehouseId();
            resolvedTransferPrice       = dto.getTransferPrice();
        }

        // 更新破损记录为 RESOLVED
        record.setStatus("RESOLVED");
        record.setCostDeduction(costDeduction);
        record.setGoodQty(goodQty);
        record.setTransferWarehouseId(resolvedTransferWarehouseId);
        record.setTransferPrice(resolvedTransferPrice);
        record.setResolvedAt(now);
        damageRecordMapper.updateById(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void writeOff(Long id, String operator) {
        // 行锁加载，防并发与重复核销
        DamageRecord record = damageRecordMapper.selectByIdForUpdate(id);
        if (record == null) throw new BusinessException("破损记录不存在");
        if (!"PENDING".equals(record.getStatus())) throw new BusinessException("该破损记录已处理");
        if (record.getOutOrderId() != null)
            throw new BusinessException("该记录已被损坏出库单占用，请先在出库管理处理");

        Product product = productMapper.selectById(record.getProductId());
        BigDecimal costPrice = product != null && product.getCostPrice() != null
                ? product.getCostPrice() : BigDecimal.ZERO;

        // 整批报废：库存已在 create() 扣减，这里只核销，不动快照/流水
        record.setStatus("RESOLVED");
        record.setCostDeduction(BigDecimal.valueOf(record.getQty()).multiply(costPrice));
        record.setGoodQty(0);
        record.setResolvedAt(LocalDateTime.now());
        damageRecordMapper.updateById(record);
    }

    private InventoryLedger buildLedger(Long productId, Long locationId, BigDecimal changeQty,
                                        String type, String docNo, String operator, LocalDateTime now) {
        InventoryLedger l = new InventoryLedger();
        l.setId(UUID.randomUUID().toString());
        l.setProductId(productId);
        l.setLocationId(locationId);
        l.setChangeQty(changeQty);
        l.setType(type);
        l.setDocumentNo(docNo);
        l.setOperator(operator);
        l.setQtyUnit("PIECE");
        l.setOccurredAt(now);
        l.setSynced(1);
        return l;
    }
}