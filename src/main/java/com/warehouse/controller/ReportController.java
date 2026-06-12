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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.inDailyReport(startDate, endDate, warehouseId));
    }

    @GetMapping("/out")
    public Result<List<Map<String, Object>>> outReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.outDailyReport(startDate, endDate, warehouseId));
    }

    @GetMapping("/inventory")
    public Result<Map<String, Object>> inventorySummary(
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.inventorySummary(warehouseId));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> dashboardStats(
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.getDashboardStats(warehouseId));
    }

    @GetMapping("/ledger")
    public Result<List<Map<String, Object>>> ledgerReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.ledgerReport(startDate, endDate, type, warehouseId));
    }

    @GetMapping("/stock-movement")
    public Result<List<Map<String, Object>>> stockMovementReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(reportService.stockMovementReport(startDate, endDate));
    }

    @GetMapping("/supplier-statement")
    public Result<List<Map<String, Object>>> supplierStatement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long supplierId) {
        return Result.success(reportService.supplierStatement(startDate, endDate, supplierId));
    }

    @GetMapping("/customer-statement")
    public Result<List<Map<String, Object>>> customerStatement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long customerId) {
        return Result.success(reportService.customerStatement(startDate, endDate, customerId));
    }

    @GetMapping("/stocktake")
    public Result<List<Map<String, Object>>> stocktakeReport(
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.stocktakeReport(warehouseId));
    }

    @GetMapping("/gross-profit")
    public Result<List<Map<String, Object>>> grossProfitReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.grossProfitReport(startDate, endDate, warehouseId));
    }

    @GetMapping("/product-profit")
    public Result<List<Map<String, Object>>> productProfitReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.productProfitReport(startDate, endDate, warehouseId));
    }

    @GetMapping("/damage")
    public Result<List<Map<String, Object>>> damageReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.damageReport(startDate, endDate, warehouseId));
    }
}
