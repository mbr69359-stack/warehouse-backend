package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.InventoryCheckDTO;
import com.warehouse.entity.Inventory;
import com.warehouse.vo.InventoryChartItemVO;
import com.warehouse.vo.InventoryStatsVO;
import java.util.List;

public interface InventoryService {
    Page<Inventory> page(int current, int size, Long warehouseId, Long productId);
    List<Inventory> listAlerts();
    void check(InventoryCheckDTO dto);
    void setAlertQty(Long warehouseId, Long productId, Integer alertQty);
    InventoryStatsVO getStats();
    List<InventoryChartItemVO> getChartData(String type, Long warehouseId);
}
