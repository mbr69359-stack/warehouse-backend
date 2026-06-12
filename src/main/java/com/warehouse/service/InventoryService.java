package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.InventoryCheckDTO;
import com.warehouse.entity.Inventory;
import com.warehouse.vo.InventoryChartItemVO;
import com.warehouse.vo.InventoryStatsVO;
import com.warehouse.vo.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface InventoryService {
    Page<Inventory> page(int current, int size, Long warehouseId, Long productId, LocalDateTime updatedAfter);
    List<Inventory> listAlerts();
    void check(InventoryCheckDTO dto, String operator);
    void setAlertQty(Long warehouseId, Long productId, Integer alertQty);
    InventoryStatsVO getStats();
    List<InventoryChartItemVO> getChartData(String type, Long warehouseId);
    ImportResultVO importOpening(MultipartFile file, String operator);
    /** 库存对账自检：流水汇总与快照逐行对比 */
    List<Map<String, Object>> auditLedgerSnapshot();
    /** 从流水重建快照并同步预警值 */
    void rebuildSnapshotFromLedger();
}
