package com.warehouse.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ReportService {
    List<Map<String, Object>> inDailyReport(LocalDate startDate, LocalDate endDate, Long warehouseId);
    List<Map<String, Object>> outDailyReport(LocalDate startDate, LocalDate endDate, Long warehouseId);
    Map<String, Object> inventorySummary(Long warehouseId);
    Map<String, Object> getDashboardStats(Long warehouseId);

    List<Map<String, Object>> ledgerReport(LocalDate startDate, LocalDate endDate, String type, Long warehouseId);
    List<Map<String, Object>> stockMovementReport(LocalDate startDate, LocalDate endDate);
    List<Map<String, Object>> supplierStatement(LocalDate startDate, LocalDate endDate, Long supplierId);
    List<Map<String, Object>> customerStatement(LocalDate startDate, LocalDate endDate, Long customerId);
    List<Map<String, Object>> stocktakeReport(Long warehouseId);
    List<Map<String, Object>> grossProfitReport(LocalDate startDate, LocalDate endDate, Long warehouseId);
    List<Map<String, Object>> productProfitReport(LocalDate startDate, LocalDate endDate, Long warehouseId);
    List<Map<String, Object>> damageReport(LocalDate startDate, LocalDate endDate, Long warehouseId);
}