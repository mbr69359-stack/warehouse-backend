package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ProductDTO;
import com.warehouse.entity.Inventory;
import com.warehouse.entity.Product;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final InventoryMapper inventoryMapper;

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
    public void delete(Long id) {
        Integer stock = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getProductId, id).gt(Inventory::getQty, 0))
                .stream().mapToInt(Inventory::getQty).sum();
        if (stock > 0) throw new RuntimeException("该商品仍有库存 " + stock + " 件，无法删除");
        productMapper.deleteById(id);
    }
}
