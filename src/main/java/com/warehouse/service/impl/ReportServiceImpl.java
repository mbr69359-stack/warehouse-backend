package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.warehouse.entity.Inventory;
import com.warehouse.mapper.InOrderMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.mapper.OutOrderMapper;
import com.warehouse.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InOrderMapper inOrderMapper;
    private final OutOrderMapper outOrderMapper;
    private final InventoryMapper inventoryMapper;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<Map<String, Object>> inDailyReport(LocalDate startDate, LocalDate endDate) {
        return inOrderMapper.selectDailyReport(
            startDate.atStartOfDay().format(DT_FMT),
            endDate.atTime(23, 59, 59).format(DT_FMT)
        );
    }

    @Override
    public List<Map<String, Object>> outDailyReport(LocalDate startDate, LocalDate endDate) {
        return outOrderMapper.selectDailyReport(
            startDate.atStartOfDay().format(DT_FMT),
            endDate.atTime(23, 59, 59).format(DT_FMT)
        );
    }

    @Override
    public Map<String, Object> inventorySummary(Long warehouseId) {
        List<Inventory> list = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(warehouseId != null, Inventory::getWarehouseId, warehouseId));
        long totalQty = list.stream().mapToLong(i -> i.getQty() != null ? i.getQty() : 0L).sum();
        long alertCount = list.stream().filter(Inventory::isAlert).count();
        Map<String, Object> m = new HashMap<>();
        m.put("totalSkus", list.size());
        m.put("totalQty", totalQty);
        m.put("alertCount", alertCount);
        return m;
    }
}
