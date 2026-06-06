package com.warehouse.service.impl;

import com.warehouse.mapper.InOrderMapper;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.mapper.OutOrderMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InOrderMapper inOrderMapper;
    private final OutOrderMapper outOrderMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLedgerMapper inventoryLedgerMapper;
    private final StockSnapshotMapper stockSnapshotMapper;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<Map<String, Object>> inDailyReport(LocalDate startDate, LocalDate endDate) {
        return inOrderMapper.selectDailyReport(
            startDate.atStartOfDay().format(DT_FMT),
            endDate.atTime(23, 59, 59).format(DT_FMT)
        );
    }

    @Override
    public List<Map<String, Object>> outDailyReport(LocalDate startDate, LocalDate endDate) {
        return outOrderMapper.selectDailyReport(
            startDate.atStartOfDay().format(DT_FMT),
            endDate.atTime(23, 59, 59).format(DT_FMT)
        );
    }

    @Override
    public Map<String, Object> inventorySummary(Long warehouseId) {
        return inventoryMapper.selectInventorySummary(warehouseId);
    }

    @Override
    public Map<String, Object> getDashboardStats() {
        return outOrderMapper.selectDashboardStats();
    }

    @Override
    public List<Map<String, Object>> ledgerReport(LocalDate startDate, LocalDate endDate, String type, Long warehouseId) {
        return inventoryLedgerMapper.selectLedgerReport(
            startDate.atStartOfDay().format(DT_FMT),
            endDate.atTime(23, 59, 59).format(DT_FMT),
            type, warehouseId);
    }

    @Override
    public List<Map<String, Object>> stockMovementReport(LocalDate startDate, LocalDate endDate) {
        return inOrderMapper.selectStockMovementReport(
            startDate.atStartOfDay().format(DT_FMT),
            endDate.atTime(23, 59, 59).format(DT_FMT));
    }

    @Override
    public List<Map<String, Object>> supplierStatement(LocalDate startDate, LocalDate endDate, Long supplierId) {
        return inOrderMapper.selectSupplierStatement(
            startDate.atStartOfDay().format(DT_FMT),
            endDate.atTime(23, 59, 59).format(DT_FMT),
            supplierId);
    }

    @Override
    public List<Map<String, Object>> customerStatement(LocalDate startDate, LocalDate endDate, Long customerId) {
        return outOrderMapper.selectCustomerStatement(
            startDate.atStartOfDay().format(DT_FMT),
            endDate.atTime(23, 59, 59).format(DT_FMT),
            customerId);
    }

    @Override
    public List<Map<String, Object>> stocktakeReport(Long warehouseId) {
        return stockSnapshotMapper.selectStocktakeReport(warehouseId);
    }
}