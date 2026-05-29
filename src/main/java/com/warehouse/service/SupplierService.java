package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.SupplierDTO;
import com.warehouse.entity.Supplier;

public interface SupplierService {
    Page<Supplier> page(int current, int size, String name);
    void create(SupplierDTO dto);
    void update(SupplierDTO dto);
    void delete(Long id);
}
