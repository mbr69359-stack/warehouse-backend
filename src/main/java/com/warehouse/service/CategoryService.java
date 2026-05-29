package com.warehouse.service;

import com.warehouse.entity.Category;
import java.util.List;

public interface CategoryService {
    List<Category> listAll();
    void create(String name, Long parentId, Integer sort);
    void delete(Long id);
}
