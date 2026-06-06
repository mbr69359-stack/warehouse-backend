package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/in")
    public Result<List<Map<String, Object>>> inReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(reportService.inDailyReport(startDate, endDate));
    }

    @GetMapping("/out")
    public Result<List<Map<String, Object>>> outReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(reportService.outDailyReport(startDate, endDate));
    }

    @GetMapping("/inventory")
    public Result<Map<String, Object>> inventorySummary(
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.inventorySummary(warehouseId));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> dashboardStats() {
        return Result.success(reportService.getDashboardStats());
    }
}
