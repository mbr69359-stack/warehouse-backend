package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ProductDTO;
import com.warehouse.entity.Product;

public interface ProductService {
    Page<Product> page(int current, int size, String name, Long categoryId);
    void create(ProductDTO dto);
    void update(ProductDTO dto);
    void delete(Long id);
}
