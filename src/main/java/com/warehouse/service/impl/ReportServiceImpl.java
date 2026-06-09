package com.warehouse.service.impl;

import com.warehouse.mapper.ExpenseMapper;
import com.warehouse.mapper.InOrderMapper;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.mapper.OutOrderMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InOrderMapper inOrderMapper;
    private final OutOrderMapper outOrderMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLedgerMapper inventoryLedgerMapper;
    private final StockSnapshotMapper stockSnapshotMapper;
    private final ExpenseMapper expenseMapper;

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
    public Map<String, Object> getDashboardStats(Long warehouseId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> invStats = stockSnapshotMapper.selectDashboardInventoryStats(warehouseId);
        if (invStats != null) result.putAll(invStats);
        if (warehouseId == null) {
            Map<String, Object> maxWh = stockSnapshotMapper.selectMaxWarehouse();
            if (maxWh != null) result.putAll(maxWh);
        }
        Map<String, Object> kpi = outOrderMapper.selectDashboardStats(warehouseId);
        if (kpi != null) result.putAll(kpi);
        return result;
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

    @Override
    public List<Map<String, Object>> grossProfitReport(LocalDate startDate, LocalDate endDate, Long warehouseId) {
        String start = startDate.atStartOfDay().format(DT_FMT);
        String end   = endDate.atTime(23, 59, 59).format(DT_FMT);
        String dateStart = startDate.toString();
        String dateEnd   = endDate.toString();

        List<Map<String, Object>> salesRows   = outOrderMapper.selectGrossProfitReport(start, end, warehouseId);
        List<Map<String, Object>> expenseRows = expenseMapper.selectExpenseSummaryByDate(dateStart, dateEnd, warehouseId);

        // 以日期字符串为 key 建索引
        Map<String, Map<String, Object>> salesByDate = salesRows.stream()
                .collect(Collectors.toMap(r -> r.get("statDate").toString(), r -> r, (a, b) -> a));
        Map<String, Map<String, Object>> expByDate = expenseRows.stream()
                .collect(Collectors.toMap(r -> r.get("expenseDate").toString(), r -> r, (a, b) -> a));

        // 全外联合所有出现过的日期
        Set<String> allDates = new TreeSet<>();
        allDates.addAll(salesByDate.keySet());
        allDates.addAll(expByDate.keySet());

        List<Map<String, Object>> result = new ArrayList<>();
        for (String date : allDates) {
            Map<String, Object> row = new LinkedHashMap<>();
            Map<String, Object> s = salesByDate.getOrDefault(date, Collections.emptyMap());
            Map<String, Object> e = expByDate.getOrDefault(date, Collections.emptyMap());

            row.put("statDate",        date);
            row.put("revenue",         s.getOrDefault("revenue",         BigDecimal.ZERO));
            row.put("cogs",            s.getOrDefault("cogs",            BigDecimal.ZERO));
            row.put("replacementLoss", s.getOrDefault("replacementLoss", BigDecimal.ZERO));
            row.put("damageLoss",      s.getOrDefault("damageLoss",      BigDecimal.ZERO));
            row.put("unloadingFee",    e.getOrDefault("unloadingFee",    BigDecimal.ZERO));
            row.put("deliveryFee",     e.getOrDefault("deliveryFee",     BigDecimal.ZERO));
            row.put("salaryFee",       e.getOrDefault("salaryFee",       BigDecimal.ZERO));
            row.put("commissionFee",   e.getOrDefault("commissionFee",   BigDecimal.ZERO));
            row.put("storageFee",      e.getOrDefault("storageFee",      BigDecimal.ZERO));
            row.put("otherFee",        e.getOrDefault("otherFee",        BigDecimal.ZERO));
            result.add(row);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> productProfitReport(LocalDate startDate, LocalDate endDate, Long warehouseId) {
        String start = startDate.atStartOfDay().format(DT_FMT);
        String end   = endDate.atTime(23, 59, 59).format(DT_FMT);
        List<Map<String, Object>> rows = outOrderMapper.selectProductProfitReport(start, end, warehouseId);
        for (Map<String, Object> row : rows) {
            BigDecimal revenue     = toBD(row.get("revenue"));
            BigDecimal grossProfit = toBD(row.get("grossProfit"));
            double margin = revenue.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : grossProfit.divide(revenue, 4, java.math.RoundingMode.HALF_UP).doubleValue();
            row.put("grossMargin", margin);
        }
        return rows;
    }

    private BigDecimal toBD(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}