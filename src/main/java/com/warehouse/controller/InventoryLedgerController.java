package com.warehouse.controller;

import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.service.InventoryLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/inventory/ledger")
@RequiredArgsConstructor
public class InventoryLedgerController {

    private final InventoryLedgerService ledgerService;

    @GetMapping
    public Result<PageResult<InventoryLedger>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(PageResult.of(
                ledgerService.page(current, size, productId, locationId, type, startDate, endDate)));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws IOException {
        ledgerService.exportLedger(productId, locationId, type, startDate, endDate, response);
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<StockSnapshot>> rebuild() {
        return Result.success(ledgerService.rebuildSnapshot());
    }
}