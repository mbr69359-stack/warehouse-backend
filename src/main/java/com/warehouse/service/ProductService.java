package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ProductDTO;
import com.warehouse.entity.Product;

public interface ProductService {
    Page<Product> page(int current, int size, String name, Long categoryId);
    void create(ProductDTO dto);
    /** @return 非空时为需要提示给用户的警告消息 */
    String update(ProductDTO dto);
    void delete(Long id);
}
