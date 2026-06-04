package com.warehouse.controller;

import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.dto.SupplierDTO;
import com.warehouse.entity.Supplier;
import com.warehouse.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.constraints.Max;

@Validated
@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {
    private final SupplierService supplierService;

    @GetMapping
    public Result<PageResult<Supplier>> page(
            @RequestParam(defaultValue = "1") int current,
            @Max(200) @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name) {
        return Result.success(PageResult.of(supplierService.page(current, size, name)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@RequestBody @Validated SupplierDTO dto) {
        supplierService.create(dto); return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SupplierDTO dto) {
        dto.setId(id); supplierService.update(dto); return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id); return Result.success();
    }
}
