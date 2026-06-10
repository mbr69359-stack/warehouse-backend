package com.warehouse.controller;

import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.dto.ProductDTO;
import com.warehouse.entity.Product;
import com.warehouse.entity.ProductCostHistory;
import com.warehouse.mapper.ProductCostHistoryMapper;
import com.warehouse.service.ProductService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@Validated
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final ProductCostHistoryMapper costHistoryMapper;

    @GetMapping
    public Result<PageResult<Product>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId) {
        return Result.success(PageResult.of(productService.page(current, size, name, categoryId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@RequestBody @Validated ProductDTO dto) {
        productService.create(dto); return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Validated ProductDTO dto) {
        dto.setId(id);
        String warning = productService.update(dto);
        Result<Void> result = Result.success();
        if (warning != null) result.setMessage(warning);
        return result;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id); return Result.success();
    }

    @GetMapping("/{id}/cost-history")
    public Result<List<ProductCostHistory>> costHistory(@PathVariable Long id) {
        return Result.success(costHistoryMapper.selectByProductId(id));
    }
}
