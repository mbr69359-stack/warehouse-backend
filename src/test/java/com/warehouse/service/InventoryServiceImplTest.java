package com.warehouse.service;

import com.warehouse.mapper.InventoryLogMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.service.impl.InventoryServiceImpl;
import com.warehouse.vo.InventoryChartItemVO;
import com.warehouse.vo.InventoryStatsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private InventoryLogMapper inventoryLogMapper;

    @InjectMocks
    private InventoryServiceImpl service;

    @Test
    void getStats_returnsTotalAndMaxWarehouse() {
        when(inventoryMapper.selectTotalQty()).thenReturn(500L);
        InventoryStatsVO maxW = new InventoryStatsVO();
        maxW.setMaxWarehouseName("仓库A");
        maxW.setMaxWarehouseQty(200L);
        maxW.setMaxWarehouseId(3L);
        when(inventoryMapper.selectMaxWarehouse()).thenReturn(maxW);

        InventoryStatsVO result = service.getStats();

        assertThat(result.getTotalQty()).isEqualTo(500L);
        assertThat(result.getMaxWarehouseName()).isEqualTo("仓库A");
        assertThat(result.getMaxWarehouseQty()).isEqualTo(200L);
        assertThat(result.getMaxWarehouseId()).isEqualTo(3L);
    }

    @Test
    void getStats_whenNoData_returnsTotalZero() {
        when(inventoryMapper.selectTotalQty()).thenReturn(null);
        when(inventoryMapper.selectMaxWarehouse()).thenReturn(null);

        InventoryStatsVO result = service.getStats();

        assertThat(result.getTotalQty()).isEqualTo(0L);
        assertThat(result.getMaxWarehouseName()).isNull();
    }

    @Test
    void getChartData_all_marksLowItems() {
        InventoryChartItemVO item1 = new InventoryChartItemVO();
        item1.setProductId(1L); item1.setProductName("手机");
        item1.setQty(420); item1.setAlertQty(50);

        InventoryChartItemVO item2 = new InventoryChartItemVO();
        item2.setProductId(2L); item2.setProductName("耳机");
        item2.setQty(18); item2.setAlertQty(30);

        when(inventoryMapper.selectChartAll()).thenReturn(Arrays.asList(item1, item2));

        List<InventoryChartItemVO> result = service.getChartData("all", null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getIsLow()).isFalse();
        assertThat(result.get(1).getIsLow()).isTrue();
    }

    @Test
    void getChartData_warehouse_callsWarehouseMapper() {
        when(inventoryMapper.selectChartByWarehouse(3L))
                .thenReturn(Collections.emptyList());

        service.getChartData("warehouse", 3L);

        verify(inventoryMapper).selectChartByWarehouse(3L);
        verify(inventoryMapper, never()).selectChartAll();
    }
}
