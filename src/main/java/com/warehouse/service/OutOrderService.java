package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.OutOrderDTO;
import com.warehouse.entity.OutOrder;
import com.warehouse.entity.OutOrderItem;
import java.util.List;

public interface OutOrderService {
    Page<OutOrder> page(int current, int size, String status, Long warehouseId);
    OutOrder getById(Long id);
    Long create(OutOrderDTO dto, Long operatorId);
    void confirm(Long orderId, List<ConfirmItemDTO> actualItems, Long operatorId);
    List<OutOrderItem> getItems(Long orderId);
    void delete(Long orderId);
}
