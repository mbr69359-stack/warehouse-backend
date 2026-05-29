package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.SupplierDTO;
import com.warehouse.entity.Supplier;
import com.warehouse.mapper.SupplierMapper;
import com.warehouse.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierMapper supplierMapper;

    @Override
    public Page<Supplier> page(int current, int size, String name) {
        LambdaQueryWrapper<Supplier> q = new LambdaQueryWrapper<Supplier>()
                .like(StringUtils.hasText(name), Supplier::getName, name)
                .orderByDesc(Supplier::getCreateTime);
        return supplierMapper.selectPage(new Page<>(current, size), q);
    }

    @Override
    public void create(SupplierDTO dto) {
        Supplier s = new Supplier();
        s.setName(dto.getName()); s.setContact(dto.getContact());
        s.setPhone(dto.getPhone()); s.setEmail(dto.getEmail());
        s.setAddress(dto.getAddress()); s.setUserId(dto.getUserId());
        s.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        supplierMapper.insert(s);
    }

    @Override
    public void update(SupplierDTO dto) {
        Supplier s = supplierMapper.selectById(dto.getId());
        if (s == null) throw new RuntimeException("供应商不存在");
        s.setName(dto.getName()); s.setContact(dto.getContact());
        s.setPhone(dto.getPhone()); s.setEmail(dto.getEmail());
        s.setAddress(dto.getAddress());
        if (dto.getStatus() != null) s.setStatus(dto.getStatus());
        supplierMapper.updateById(s);
    }

    @Override
    public void delete(Long id) { supplierMapper.deleteById(id); }
}
