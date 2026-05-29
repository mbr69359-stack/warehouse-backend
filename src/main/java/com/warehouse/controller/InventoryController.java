package com.warehouse.controller;

import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.dto.InventoryCheckDTO;
import com.warehouse.entity.Inventory;
import com.warehouse.service.InventoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping
    public Result<PageResult<Inventory>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId) {
        return Result.success(PageResult.of(inventoryService.page(current, size, warehouseId, productId)));
    }

    @GetMapping("/alerts")
    public Result<List<Inventory>> alerts() {
        return Result.success(inventoryService.listAlerts());
    }

    @PostMapping("/check")
    public Result<Void> check(@RequestBody @Validated InventoryCheckDTO dto) {
        inventoryService.check(dto); return Result.success();
    }

    @PutMapping("/alert")
    public Result<Void> setAlert(@RequestBody AlertReq req) {
        inventoryService.setAlertQty(req.getWarehouseId(), req.getProductId(), req.getAlertQty());
        return Result.success();
    }

    @Data
    static class AlertReq {
        private Long warehouseId;
        private Long productId;
        private Integer alertQty;
    }
}
