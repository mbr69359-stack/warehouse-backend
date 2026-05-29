package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.warehouse.entity.Inventory;
import com.warehouse.mapper.InOrderMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.mapper.OutOrderMapper;
import com.warehouse.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InOrderMapper inOrderMapper;
    private final OutOrderMapper outOrderMapper;
    private final InventoryMapper inventoryMapper;

    @Override
    public List<Map<String, Object>> inDailyReport(String startDate, String endDate) {
        return inOrderMapper.selectDailyReport(startDate + " 00:00:00", endDate + " 23:59:59");
    }

    @Override
    public List<Map<String, Object>> outDailyReport(String startDate, String endDate) {
        return outOrderMapper.selectDailyReport(startDate + " 00:00:00", endDate + " 23:59:59");
    }

    @Override
    public Map<String, Object> inventorySummary(Long warehouseId) {
        List<Inventory> list = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(warehouseId != null, Inventory::getWarehouseId, warehouseId));
        long totalQty = list.stream().mapToLong(Inventory::getQty).sum();
        long alertCount = list.stream().filter(Inventory::isAlert).count();
        Map<String, Object> m = new HashMap<>();
        m.put("totalSkus", list.size());
        m.put("totalQty", totalQty);
        m.put("alertCount", alertCount);
        return m;
    }
}
