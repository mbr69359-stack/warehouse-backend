package com.warehouse.service;

import java.util.List;
import java.util.Map;

public interface ReportService {
    List<Map<String, Object>> inDailyReport(String startDate, String endDate);
    List<Map<String, Object>> outDailyReport(String startDate, String endDate);
    Map<String, Object> inventorySummary(Long warehouseId);
}
