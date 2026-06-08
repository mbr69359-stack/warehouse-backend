package com.warehouse.service;

import com.warehouse.entity.Expense;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseService {
    List<Expense> list(LocalDate startDate, LocalDate endDate, Long warehouseId, String type);
    Expense create(Expense expense);
    Expense update(Long id, Expense expense);
    void delete(Long id);
}