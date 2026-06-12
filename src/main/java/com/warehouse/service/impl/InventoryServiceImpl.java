package com.warehouse.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.InventoryCheckDTO;
import com.warehouse.dto.InventoryImportRow;
import com.warehouse.entity.Inventory;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.mapper.WarehouseMapper;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.common.BusinessException;
import com.warehouse.service.InventoryService;
import com.warehouse.vo.InventoryChartItemVO;
import com.warehouse.vo.InventoryStatsVO;
import com.warehouse.vo.ImportResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final ConcurrentHashMap<Long, Boolean> CHECKING = new ConcurrentHashMap<>();

    private final InventoryMapper inventoryMapper;
    private final InventoryLedgerMapper inventoryLedgerMapper;
    private final StockSnapshotMapper stockSnapshotMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;

    @Override
    public Page<Inventory> page(int current, int size, Long warehouseId, Long productId, LocalDateTime updatedAfter) {
        return inventoryMapper.selectPageFromSnapshot(new Page<>(current, size), warehouseId, productId, updatedAfter);
    }

    @Override
    public List<Inventory> listAlerts() {
        return inventoryMapper.selectAlertsFromSnapshot();
    }

    @Override
    @Transactional
    public void check(InventoryCheckDTO dto, String operator) {
        if (dto.getItems() == null) return;
        Long warehouseId = dto.getWarehouseId();
        if (CHECKING.putIfAbsent(warehouseId, Boolean.TRUE) != null) {
            throw new BusinessException("该仓库正在盘点中，请等待操作完成后再提交");
        }
        String checkNo = "CK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        try {
            for (InventoryCheckDTO.CheckItem ci : dto.getItems()) {
                int actualQty = ci.getActualQty() != null ? ci.getActualQty() : 0;
                StockSnapshot snap = stockSnapshotMapper.selectOneForUpdate(ci.getProductId(), warehouseId);
                int beforeQty = snap != null && snap.getCurrentQty() != null
                        ? snap.getCurrentQty().intValue() : 0;
                int alertQty = snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0;
                int delta = actualQty - beforeQty;
                if (delta != 0) {
                    InventoryLedger ledger = new InventoryLedger();
                    ledger.setId(UUID.randomUUID().toString());
                    ledger.setProductId(ci.getProductId());
                    ledger.setLocationId(warehouseId);
                    ledger.setChangeQty(BigDecimal.valueOf(delta));
                    ledger.setType("adjust");
                    ledger.setDocumentNo(checkNo);
                    ledger.setOperator(operator != null ? operator : "");
                    ledger.setNote(dto.getRemark());
                    ledger.setOccurredAt(LocalDateTime.now());
                    ledger.setSynced(1);
                    ledger.setCreatedAt(LocalDateTime.now());
                    inventoryLedgerMapper.insert(ledger);
                }
                stockSnapshotMapper.upsert(
                    ci.getProductId(), warehouseId,
                    BigDecimal.valueOf(actualQty),
                    alertQty
                );
            }
        } finally {
            CHECKING.remove(warehouseId);
        }
    }

    @Override
    public void setAlertQty(Long warehouseId, Long productId, Integer alertQty) {
        if (alertQty == null || alertQty < 0) throw new BusinessException("预警数量不能为负数");
        try {
            StockSnapshot snap = stockSnapshotMapper.selectOne(productId, warehouseId);
            if (snap == null) throw new BusinessException("库存记录不存在，请先入库");
            stockSnapshotMapper.updateAlertQty(productId, warehouseId, alertQty);
            inventoryMapper.update(null, new LambdaUpdateWrapper<Inventory>()
                    .eq(Inventory::getWarehouseId, warehouseId)
                    .eq(Inventory::getProductId, productId)
                    .set(Inventory::getAlertQty, alertQty));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("操作库存记录异常：" + e.getMessage());
        }
    }

    @Override
    public InventoryStatsVO getStats() {
        InventoryStatsVO result = new InventoryStatsVO();
        Long total = inventoryMapper.selectTotalQtyFromSnapshot();
        result.setTotalQty(total != null ? total : 0L);
        InventoryStatsVO maxW = inventoryMapper.selectMaxWarehouseFromSnapshot();
        if (maxW != null) {
            result.setMaxWarehouseName(maxW.getMaxWarehouseName());
            result.setMaxWarehouseQty(maxW.getMaxWarehouseQty());
            result.setMaxWarehouseId(maxW.getMaxWarehouseId());
        }
        return result;
    }

    @Override
    public List<InventoryChartItemVO> getChartData(String type, Long warehouseId) {
        List<InventoryChartItemVO> items;
        if ("warehouse".equals(type) && warehouseId != null) {
            items = inventoryMapper.selectChartByWarehouseFromSnapshot(warehouseId);
        } else {
            items = inventoryMapper.selectChartAllFromSnapshot();
        }
        items.forEach(item -> item.setIsLow(
            item.getAlertQty() != null && item.getAlertQty() > 0 &&
            item.getQty() != null && item.getQty() < item.getAlertQty()
        ));
        return items;
    }

    @Override
    public List<Map<String, Object>> auditLedgerSnapshot() {
        return stockSnapshotMapper.selectLedgerSnapshotAudit();
    }

    @Override
    @Transactional
    public void rebuildSnapshotFromLedger() {
        // 从流水重建快照（rebuild 本身按流水求和覆盖快照，账实天然一致），再同步预警值
        stockSnapshotMapper.rebuildAllFromLedger();
        stockSnapshotMapper.syncAlertQtyFromInventory();
    }

    @Override
    @Transactional
    public ImportResultVO importOpening(MultipartFile file, String operator) {
        List<InventoryImportRow> rows = new ArrayList<>();
        try {
            EasyExcel.read(file.getInputStream(), InventoryImportRow.class,
                    new PageReadListener<InventoryImportRow>(rows::addAll)).sheet().doRead();
        } catch (Exception e) {
            throw new BusinessException("Excel解析失败：" + e.getMessage());
        }

        int successCount = 0, failCount = 0;
        List<String> failDetails = new ArrayList<>();

        // 防重复导入：检查文件中涉及的仓库是否已有期初流水，避免误操作导致库存翻倍
        java.util.Set<String> warehouseNamesInFile = new java.util.HashSet<>();
        for (InventoryImportRow row : rows) {
            if (row.getWarehouseName() != null && !row.getWarehouseName().trim().isEmpty())
                warehouseNamesInFile.add(row.getWarehouseName().trim());
        }
        List<String> duplicateWarehouses = new ArrayList<>();
        for (String whName : warehouseNamesInFile) {
            Warehouse wh = warehouseMapper.selectOne(
                    new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getName, whName));
            if (wh != null) {
                long count = inventoryLedgerMapper.selectCount(
                        new LambdaQueryWrapper<InventoryLedger>()
                                .eq(InventoryLedger::getType, "opening")
                                .eq(InventoryLedger::getLocationId, wh.getId()));
                if (count > 0) duplicateWarehouses.add(whName);
            }
        }
        if (!duplicateWarehouses.isEmpty()) {
            throw new BusinessException("仓库「" + String.join("」「", duplicateWarehouses) +
                    "」已有期初库存记录，重复导入会累加库存。如需重新导入请先联系管理员清除旧期初数据。");
        }

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2;
            InventoryImportRow row = rows.get(i);

            if (row.getSkuCode() == null || row.getSkuCode().trim().isEmpty()) {
                failDetails.add("第" + rowNum + "行: SKU码不能为空"); failCount++; continue;
            }
            if (row.getWarehouseName() == null || row.getWarehouseName().trim().isEmpty()) {
                failDetails.add("第" + rowNum + "行: 仓库名称不能为空"); failCount++; continue;
            }
            if (row.getQty() == null || row.getQty() <= 0) {
                failDetails.add("第" + rowNum + "行: 数量必须为正整数"); failCount++; continue;
            }

            Product product = productMapper.selectOne(
                    new LambdaQueryWrapper<Product>().eq(Product::getSkuCode, row.getSkuCode()));
            if (product == null) {
                failDetails.add("第" + rowNum + "行: SKU [" + row.getSkuCode() + "] 不存在"); failCount++; continue;
            }

            Warehouse warehouse = warehouseMapper.selectOne(
                    new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getName, row.getWarehouseName()));
            if (warehouse == null) {
                failDetails.add("第" + rowNum + "行: 仓库 [" + row.getWarehouseName() + "] 不存在"); failCount++; continue;
            }

            StockSnapshot snap = stockSnapshotMapper.selectOne(product.getId(), warehouse.getId());
            BigDecimal currentQty = snap != null && snap.getCurrentQty() != null ? snap.getCurrentQty() : BigDecimal.ZERO;
            int alertQty = snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0;
            BigDecimal newQty = currentQty.add(BigDecimal.valueOf(row.getQty()));

            InventoryLedger ledger = new InventoryLedger();
            ledger.setId(UUID.randomUUID().toString());
            ledger.setProductId(product.getId());
            ledger.setLocationId(warehouse.getId());
            ledger.setChangeQty(BigDecimal.valueOf(row.getQty()));
            ledger.setType("opening");
            ledger.setOperator(operator != null ? operator : "");
            ledger.setNote("期初库存导入");
            ledger.setOccurredAt(LocalDateTime.now());
            ledger.setSynced(1);
            ledger.setCreatedAt(LocalDateTime.now());
            inventoryLedgerMapper.insert(ledger);

            stockSnapshotMapper.upsert(product.getId(), warehouse.getId(), newQty, alertQty);
            successCount++;
        }

        ImportResultVO result = new ImportResultVO();
        result.setSuccessCount(successCount);
        result.setFailCount(failCount);
        result.setFailDetails(failDetails);
        return result;
    }
}
