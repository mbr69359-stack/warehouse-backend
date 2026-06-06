package com.warehouse.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ReportService {
    List<Map<String, Object>> inDailyReport(LocalDate startDate, LocalDate endDate);
    List<Map<String, Object>> outDailyReport(LocalDate startDate, LocalDate endDate);
    Map<String, Object> inventorySummary(Long warehouseId);
    Map<String, Object> getDashboardStats();
}
