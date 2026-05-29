package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.entity.Category;
import com.warehouse.service.CategoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public Result<List<Category>> list() { return Result.success(categoryService.listAll()); }

    @PostMapping
    public Result<Void> create(@RequestBody CategoryReq req) {
        categoryService.create(req.getName(), req.getParentId(), req.getSort());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id); return Result.success();
    }

    @Data
    static class CategoryReq {
        private String name;
        private Long parentId;
        private Integer sort;
    }
}
