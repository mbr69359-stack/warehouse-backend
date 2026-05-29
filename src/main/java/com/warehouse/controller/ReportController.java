package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/in")
    public Result<List<Map<String, Object>>> inReport(
            @RequestParam String startDate, @RequestParam String endDate) {
        return Result.success(reportService.inDailyReport(startDate, endDate));
    }

    @GetMapping("/out")
    public Result<List<Map<String, Object>>> outReport(
            @RequestParam String startDate, @RequestParam String endDate) {
        return Result.success(reportService.outDailyReport(startDate, endDate));
    }

    @GetMapping("/inventory")
    public Result<Map<String, Object>> inventorySummary(
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.inventorySummary(warehouseId));
    }
}
