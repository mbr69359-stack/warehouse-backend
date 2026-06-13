package com.warehouse.service;

import com.warehouse.common.BusinessException;
import com.warehouse.dto.InOrderDTO;
import com.warehouse.entity.InOrder;
import com.warehouse.entity.InOrderItem;
import com.warehouse.mapper.CustomerReturnMapper;
import com.warehouse.mapper.DamageRecordMapper;
import com.warehouse.mapper.InOrderItemMapper;
import com.warehouse.mapper.InOrderMapper;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.ProductCostHistoryMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.mapper.WarehouseMapper;
import com.warehouse.service.impl.InOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InOrderUpdateTest {
    @Mock InOrderMapper inOrderMapper;
    @Mock InOrderItemMapper inOrderItemMapper;
    @Mock InventoryLedgerMapper ledgerMapper;
    @Mock StockSnapshotMapper snapshotMapper;
    @Mock CustomerReturnMapper customerReturnMapper;
    @Mock DamageRecordMapper damageRecordMapper;
    @Mock ProductMapper productMapper;
    @Mock ProductCostHistoryMapper costHistoryMapper;
    @Mock WarehouseMapper warehouseMapper;

    InOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InOrderServiceImpl(inOrderMapper, inOrderItemMapper, ledgerMapper, snapshotMapper,
                customerReturnMapper, damageRecordMapper, productMapper, costHistoryMapper, warehouseMapper);
    }

    private InOrder order(String status, String type) {
        InOrder o = new InOrder();
        o.setId(1L); o.setStatus(status); o.setType(type); o.setOrderNo("IN-1");
        return o;
    }
    private InOrderDTO.Item item(Long pid, Integer planQty, String price) {
        InOrderDTO.Item i = new InOrderDTO.Item();
        i.setProductId(pid); i.setPlanQty(planQty); i.setPrice(new BigDecimal(price));
        return i;
    }
    private InOrderDTO dto(String type, InOrderDTO.Item... items) {
        InOrderDTO d = new InOrderDTO();
        d.setWarehouseId(10L); d.setType(type); d.setItems(Arrays.asList(items));
        return d;
    }

    @Test
    void update_draftPurchase_replacesItemsNoInventory() {
        when(inOrderMapper.selectByIdForUpdate(1L)).thenReturn(order("DRAFT", "PURCHASE"));

        service.update(1L, dto("PURCHASE", item(2L, 5, "3.00")), 99L);

        verify(inOrderItemMapper).delete(any());
        verify(inOrderItemMapper, times(1)).insert(any(InOrderItem.class));
        verify(inOrderMapper).updateById(any(InOrder.class));
        verify(snapshotMapper, never()).upsert(anyLong(), anyLong(), any(BigDecimal.class), anyInt());
        verify(ledgerMapper, never()).insert(any());
    }

    @Test
    void update_notDraft_throws() {
        when(inOrderMapper.selectByIdForUpdate(1L)).thenReturn(order("CONFIRMED", "PURCHASE"));
        assertThatThrownBy(() -> service.update(1L, dto("PURCHASE", item(2L, 1, "1.00")), 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("只有草稿");
    }

    @Test
    void update_returnInType_throws() {
        when(inOrderMapper.selectByIdForUpdate(1L)).thenReturn(order("DRAFT", "RETURN_IN"));
        assertThatThrownBy(() -> service.update(1L, dto("PURCHASE", item(2L, 1, "1.00")), 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("系统生成");
    }
}
