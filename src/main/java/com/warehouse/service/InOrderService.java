package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.InOrderDTO;
import com.warehouse.entity.InOrder;
import com.warehouse.entity.InOrderItem;
import java.util.List;

public interface InOrderService {
    Page<InOrder> page(int current, int size, String status, Long warehouseId, Long supplierId);
    Long create(InOrderDTO dto, Long operatorId);
    void confirm(Long orderId, List<ConfirmItemDTO> actualItems, Long operatorId);
    List<InOrderItem> getItems(Long orderId);
    void delete(Long orderId, Long operatorId);
}
