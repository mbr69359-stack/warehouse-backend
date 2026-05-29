package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ProductDTO;
import com.warehouse.entity.Product;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    @Override
    public Page<Product> page(int current, int size, String name, Long categoryId) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .like(StringUtils.hasText(name), Product::getName, name)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .orderByDesc(Product::getCreateTime);
        return productMapper.selectPage(new Page<>(current, size), q);
    }

    @Override
    public void create(ProductDTO dto) {
        Product p = new Product();
        p.setName(dto.getName()); p.setSkuCode(dto.getSkuCode());
        p.setCategoryId(dto.getCategoryId()); p.setUnit(dto.getUnit());
        p.setPrice(dto.getPrice()); p.setImage(dto.getImage());
        p.setRemark(dto.getRemark());
        p.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        productMapper.insert(p);
    }

    @Override
    public void update(ProductDTO dto) {
        Product p = productMapper.selectById(dto.getId());
        if (p == null) throw new RuntimeException("商品不存在");
        p.setName(dto.getName()); p.setCategoryId(dto.getCategoryId());
        p.setUnit(dto.getUnit()); p.setPrice(dto.getPrice());
        p.setImage(dto.getImage()); p.setRemark(dto.getRemark());
        if (dto.getStatus() != null) p.setStatus(dto.getStatus());
        productMapper.updateById(p);
    }

    @Override
    public void delete(Long id) { productMapper.deleteById(id); }
}
