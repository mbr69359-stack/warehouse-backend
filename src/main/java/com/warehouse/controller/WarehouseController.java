package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.dto.WarehouseDTO;
import com.warehouse.entity.Warehouse;
import com.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/warehouses")
@RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseService warehouseService;

    @GetMapping
    public Result<List<Warehouse>> list() { return Result.success(warehouseService.listAll()); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@RequestBody @Validated WarehouseDTO dto) {
        warehouseService.create(dto); return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @RequestBody WarehouseDTO dto) {
        dto.setId(id); warehouseService.update(dto); return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id); return Result.success();
    }
}
