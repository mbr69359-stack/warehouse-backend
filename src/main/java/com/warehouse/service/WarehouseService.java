package com.warehouse.service;

import com.warehouse.dto.WarehouseDTO;
import com.warehouse.entity.Warehouse;
import java.util.List;

public interface WarehouseService {
    List<Warehouse> listAll();
    void create(WarehouseDTO dto);
    void update(WarehouseDTO dto);
    void delete(Long id);
}
