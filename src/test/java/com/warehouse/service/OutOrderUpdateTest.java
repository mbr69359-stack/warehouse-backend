package com.warehouse.service;

import com.warehouse.common.BusinessException;
import com.warehouse.dto.OutOrderDTO;
import com.warehouse.entity.OutOrder;
import com.warehouse.entity.OutOrderItem;
import com.warehouse.entity.Product;
import com.warehouse.mapper.CustomerMapper;
import com.warehouse.mapper.CustomerReturnMapper;
import com.warehouse.mapper.DamageRecordMapper;
import com.warehouse.mapper.ExpenseMapper;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.OutOrderItemMapper;
import com.warehouse.mapper.OutOrderMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.mapper.WarehouseMapper;
import com.warehouse.service.impl.OutOrderServiceImpl;
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
class OutOrderUpdateTest {
    @Mock OutOrderMapper outOrderMapper;
    @Mock OutOrderItemMapper outOrderItemMapper;
    @Mock InventoryLedgerMapper ledgerMapper;
    @Mock StockSnapshotMapper snapshotMapper;
    @Mock WarehouseMapper warehouseMapper;
    @Mock DamageRecordMapper damageRecordMapper;
    @Mock CustomerReturnMapper customerReturnMapper;
    @Mock CustomerMapper customerMapper;
    @Mock ProductMapper productMapper;
    @Mock ExpenseMapper expenseMapper;
    @Mock SysConfigService sysConfigService;

    OutOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OutOrderServiceImpl(outOrderMapper, outOrderItemMapper, ledgerMapper, snapshotMapper,
                warehouseMapper, damageRecordMapper, customerReturnMapper, customerMapper, productMapper,
                expenseMapper, sysConfigService);
    }

    private OutOrder order(String status, String type) {
        OutOrder o = new OutOrder();
        o.setId(1L); o.setStatus(status); o.setType(type); o.setOrderNo("OUT-1");
        return o;
    }
    private OutOrderDTO.Item item(Long pid, Integer qty, String price) {
        OutOrderDTO.Item i = new OutOrderDTO.Item();
        i.setProductId(pid); i.setQty(qty); i.setPrice(new BigDecimal(price));
        return i;
    }
    private OutOrderDTO dto(String type, OutOrderDTO.Item... items) {
        OutOrderDTO d = new OutOrderDTO();
        d.setWarehouseId(10L); d.setType(type); d.setItems(Arrays.asList(items));
        return d;
    }
    private OutOrderDTO dtoSale() {
        OutOrderDTO d = dto("SALE", item(2L, 1, "1.00"));
        d.setSaleChannel("RETAIL");
        return d;
    }

    @Test
    void update_draftSale_replacesItemsNoInventory() {
        when(outOrderMapper.selectByIdForUpdate(1L)).thenReturn(order("DRAFT", "SALE"));
        Product p = new Product(); p.setId(2L); p.setCostPrice(new BigDecimal("4.00"));
        when(productMapper.selectById(2L)).thenReturn(p);
        OutOrderDTO d = dto("SALE", item(2L, 7, "9.90"));
        d.setSaleChannel("RETAIL");

        service.update(1L, d, 99L);

        verify(outOrderItemMapper).delete(any());
        verify(outOrderItemMapper, times(1)).insert(any(OutOrderItem.class));
        verify(outOrderMapper).updateById(any(OutOrder.class));
        verify(snapshotMapper, never()).upsert(anyLong(), anyLong(), any(BigDecimal.class), anyInt());
        verify(ledgerMapper, never()).insert(any());
    }

    @Test
    void update_notDraft_throws() {
        when(outOrderMapper.selectByIdForUpdate(1L)).thenReturn(order("CONFIRMED", "SALE"));
        assertThatThrownBy(() -> service.update(1L, dtoSale(), 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("只有草稿");
    }

    @Test
    void update_systemGeneratedType_throws() {
        when(outOrderMapper.selectByIdForUpdate(1L)).thenReturn(order("DRAFT", "DAMAGE_OUT"));
        assertThatThrownBy(() -> service.update(1L, dtoSale(), 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("系统生成");
    }

    @Test
    void update_transferMissingTarget_throws() {
        when(outOrderMapper.selectByIdForUpdate(1L)).thenReturn(order("DRAFT", "TRANSFER"));
        OutOrderDTO d = dto("TRANSFER", item(2L, 1, "1.00"));
        assertThatThrownBy(() -> service.update(1L, d, 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("目标仓库");
    }
}
