package com.warehouse.controller;

import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.dto.InventoryCheckDTO;
import com.warehouse.entity.Inventory;
import com.warehouse.service.InventoryService;
import com.warehouse.vo.InventoryChartItemVO;
import com.warehouse.vo.InventoryStatsVO;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@Validated
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
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String updatedAfter) {
        LocalDateTime after = updatedAfter != null ? LocalDateTime.parse(updatedAfter) : null;
        return Result.success(PageResult.of(inventoryService.page(current, size, warehouseId, productId, after)));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Inventory>> alerts() {
        return Result.success(inventoryService.listAlerts());
    }

    @PostMapping("/check")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> check(@RequestBody @Validated InventoryCheckDTO dto) {
        inventoryService.check(dto); return Result.success();
    }

    @PutMapping("/alert")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> setAlert(@RequestBody @Validated AlertReq req) {
        inventoryService.setAlertQty(req.getWarehouseId(), req.getProductId(), req.getAlertQty());
        return Result.success();
    }

    @GetMapping("/stats")
    public Result<InventoryStatsVO> stats() {
        return Result.success(inventoryService.getStats());
    }

    @GetMapping("/chart")
    public Result<List<InventoryChartItemVO>> chart(
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(inventoryService.getChartData(type, warehouseId));
    }

    @Data
    static class AlertReq {
        @NotNull(message = "仓库不能为空")
        private Long warehouseId;
        @NotNull(message = "商品不能为空")
        private Long productId;
        @NotNull(message = "预警数量不能为空")
        @Min(value = 0, message = "预警数量不能为负数")
        private Integer alertQty;
    }
}
