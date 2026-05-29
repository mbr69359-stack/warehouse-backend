package com.warehouse.service.impl;

import com.warehouse.entity.Category;
import com.warehouse.mapper.CategoryMapper;
import com.warehouse.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<Category> listAll() { return categoryMapper.selectList(null); }

    @Override
    public void create(String name, Long parentId, Integer sort) {
        Category c = new Category();
        c.setName(name);
        c.setParentId(parentId != null ? parentId : 0L);
        c.setSort(sort != null ? sort : 0);
        categoryMapper.insert(c);
    }

    @Override
    public void delete(Long id) { categoryMapper.deleteById(id); }
}
