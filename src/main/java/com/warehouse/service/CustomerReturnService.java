package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.CustomerReturnDTO;
import com.warehouse.entity.CustomerReturn;
import com.warehouse.entity.CustomerReturnItem;
import java.util.List;

public interface CustomerReturnService {
    Page<CustomerReturn> page(int current, int size, Long warehouseId);
    Long create(CustomerReturnDTO dto, String createdBy, Long operatorId);
    List<CustomerReturnItem> listItems(Long returnId);
    void confirm(Long returnId, List<ConfirmItemDTO> items, Long operatorId);
}
