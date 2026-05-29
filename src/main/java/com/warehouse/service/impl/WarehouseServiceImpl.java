package com.warehouse.service.impl;

import com.warehouse.dto.WarehouseDTO;
import com.warehouse.entity.Warehouse;
import com.warehouse.mapper.WarehouseMapper;
import com.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseMapper warehouseMapper;

    @Override
    public List<Warehouse> listAll() { return warehouseMapper.selectList(null); }

    @Override
    public void create(WarehouseDTO dto) {
        Warehouse w = new Warehouse();
        w.setName(dto.getName()); w.setAddress(dto.getAddress());
        w.setManagerId(dto.getManagerId()); w.setRemark(dto.getRemark());
        w.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        warehouseMapper.insert(w);
    }

    @Override
    public void update(WarehouseDTO dto) {
        Warehouse w = warehouseMapper.selectById(dto.getId());
        if (w == null) throw new RuntimeException("仓库不存在");
        w.setName(dto.getName()); w.setAddress(dto.getAddress());
        w.setManagerId(dto.getManagerId()); w.setRemark(dto.getRemark());
        if (dto.getStatus() != null) w.setStatus(dto.getStatus());
        warehouseMapper.updateById(w);
    }

    @Override
    public void delete(Long id) { warehouseMapper.deleteById(id); }
}
