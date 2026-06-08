package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.entity.Expense;
import com.warehouse.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public Result<List<Expense>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String type) {
        return Result.success(expenseService.list(startDate, endDate, warehouseId, type));
    }

    @PostMapping
    public Result<Expense> create(@RequestBody Expense expense) {
        return Result.success(expenseService.create(expense));
    }

    @PutMapping("/{id}")
    public Result<Expense> update(@PathVariable Long id, @RequestBody Expense expense) {
        return Result.success(expenseService.update(id, expense));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return Result.success(null);
    }
}