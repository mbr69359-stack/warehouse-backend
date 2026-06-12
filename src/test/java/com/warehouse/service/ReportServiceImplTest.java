package com.warehouse.service;

import com.warehouse.mapper.DamageRecordMapper;
import com.warehouse.mapper.ExpenseMapper;
import com.warehouse.mapper.InOrderMapper;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.mapper.OutOrderMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.mapper.WarehouseMapper;
import com.warehouse.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private InOrderMapper inOrderMapper;
    @Mock
    private OutOrderMapper outOrderMapper;
    @Mock
    private InventoryMapper inventoryMapper;
    @Mock
    private InventoryLedgerMapper inventoryLedgerMapper;
    @Mock
    private StockSnapshotMapper stockSnapshotMapper;
    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private DamageRecordMapper damageRecordMapper;

    @InjectMocks
    private ReportServiceImpl service;

    @Test
    void inventorySummary_includesBoxStatsForBoxDisplay() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalSkus", 2L);
        summary.put("totalQty", 98L);
        summary.put("alertCount", 0L);
        when(inventoryMapper.selectInventorySummary(null)).thenReturn(summary);

        Map<String, Object> boxStats = new HashMap<>();
        boxStats.put("totalBoxCount", 2L);
        boxStats.put("looseCount", 2L);
        when(stockSnapshotMapper.selectTotalBoxStats(null)).thenReturn(boxStats);

        Map<String, Object> result = service.inventorySummary(null);

        assertThat(result).containsEntry("totalQty", 98L);
        assertThat(result).containsEntry("totalBoxCount", 2L);
        assertThat(result).containsEntry("looseCount", 2L);
    }
}
