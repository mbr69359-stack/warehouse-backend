package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.warehouse.entity.Expense;
import com.warehouse.mapper.ExpenseMapper;
import com.warehouse.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseMapper expenseMapper;

    @Override
    public List<Expense> list(LocalDate startDate, LocalDate endDate, Long warehouseId, String type) {
        LambdaQueryWrapper<Expense> q = new LambdaQueryWrapper<Expense>()
                .ge(startDate != null, Expense::getExpenseDate, startDate)
                .le(endDate != null, Expense::getExpenseDate, endDate)
                .eq(warehouseId != null, Expense::getWarehouseId, warehouseId)
                .eq(type != null && !type.isEmpty(), Expense::getType, type)
                .orderByDesc(Expense::getExpenseDate);
        return expenseMapper.selectList(q);
    }

    @Override
    public Expense create(Expense expense) {
        expenseMapper.insert(expense);
        return expense;
    }

    @Override
    public Expense update(Long id, Expense expense) {
        expense.setId(id);
        expenseMapper.updateById(expense);
        return expenseMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        expenseMapper.deleteById(id);
    }
}