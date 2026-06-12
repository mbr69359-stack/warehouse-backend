package com.warehouse.service;

import com.warehouse.entity.InOrder;
import com.warehouse.entity.InOrderItem;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.OutOrder;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.CustomerMapper;
import com.warehouse.mapper.CustomerReturnMapper;
import com.warehouse.mapper.DamageRecordMapper;
import com.warehouse.mapper.ExpenseMapper;
import com.warehouse.mapper.InOrderItemMapper;
import com.warehouse.mapper.InOrderMapper;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.OutOrderItemMapper;
import com.warehouse.mapper.OutOrderMapper;
import com.warehouse.mapper.ProductCostHistoryMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.mapper.WarehouseMapper;
import com.warehouse.service.impl.InOrderServiceImpl;
import com.warehouse.service.impl.OutOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDeleteInventoryLedgerIsolationTest {

    private static final Long SAME_ORDER_ID = 1L;

    @Mock
    private InOrderMapper inOrderMapper;
    @Mock
    private InOrderItemMapper inOrderItemMapper;
    @Mock
    private OutOrderMapper outOrderMapper;
    @Mock
    private OutOrderItemMapper outOrderItemMapper;
    @Mock
    private InventoryLedgerMapper ledgerMapper;
    @Mock
    private StockSnapshotMapper snapshotMapper;
    @Mock
    private CustomerReturnMapper customerReturnMapper;
    @Mock
    private DamageRecordMapper damageRecordMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductCostHistoryMapper costHistoryMapper;
    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private SysConfigService sysConfigService;

    @InjectMocks
    private InOrderServiceImpl inOrderService;
    @InjectMocks
    private OutOrderServiceImpl outOrderService;

    @Test
    void deletingConfirmedInOrderWithSharedNumericIdAppendsInboundCancelLedgerOnly() {
        Long warehouseId = 10L;
        Long productId = 100L;
        String inOrderNo = "IN-SAME-ID";

        InOrder order = new InOrder();
        order.setId(SAME_ORDER_ID);
        order.setOrderNo(inOrderNo);
        order.setWarehouseId(warehouseId);
        order.setType("PURCHASE");
        order.setStatus("CONFIRMED");
        when(inOrderMapper.selectByIdForUpdate(SAME_ORDER_ID)).thenReturn(order);

        InOrderItem item = new InOrderItem();
        item.setOrderId(SAME_ORDER_ID);
        item.setProductId(productId);
        item.setPlanQty(3);
        item.setActualQty(3);
        when(inOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(ledgerMapper.selectList(any())).thenReturn(
                Collections.singletonList(originalLedger(productId, warehouseId, "inbound", "3")));
        when(snapshotMapper.selectOneForUpdate(productId, warehouseId)).thenReturn(snapshot(productId, warehouseId, "20"));

        inOrderService.delete(SAME_ORDER_ID, 7L);

        ArgumentCaptor<InventoryLedger> ledger = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(ledgerMapper).insert(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo("inbound_cancel");
        assertThat(ledger.getValue().getDocumentNo()).isEqualTo(inOrderNo);
        assertThat(ledger.getValue().getChangeQty()).isEqualByComparingTo("-3");
        verify(ledgerMapper).selectList(any());
        verifyNoMoreInteractions(ledgerMapper);
    }

    @Test
    void deletingConfirmedOutOrderWithSharedNumericIdAppendsOutboundCancelLedgerOnly() {
        Long warehouseId = 20L;
        Long productId = 200L;
        String outOrderNo = "OUT-SAME-ID";

        OutOrder order = new OutOrder();
        order.setId(SAME_ORDER_ID);
        order.setOrderNo(outOrderNo);
        order.setWarehouseId(warehouseId);
        order.setType("NORMAL");
        order.setStatus("CONFIRMED");
        when(outOrderMapper.selectByIdForUpdate(SAME_ORDER_ID)).thenReturn(order);

        when(ledgerMapper.selectList(any())).thenReturn(
                Collections.singletonList(originalLedger(productId, warehouseId, "outbound", "-3")));
        when(snapshotMapper.selectOneForUpdate(productId, warehouseId)).thenReturn(snapshot(productId, warehouseId, "5"));

        outOrderService.delete(SAME_ORDER_ID, 8L);

        ArgumentCaptor<InventoryLedger> ledger = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(ledgerMapper).insert(ledger.capture());
        assertThat(ledger.getValue().getType()).isEqualTo("outbound_cancel");
        assertThat(ledger.getValue().getDocumentNo()).isEqualTo(outOrderNo);
        assertThat(ledger.getValue().getChangeQty()).isEqualByComparingTo("3");
        verify(ledgerMapper).selectList(any());
        verifyNoMoreInteractions(ledgerMapper);
    }

    private InventoryLedger originalLedger(Long productId, Long locationId, String type, String changeQty) {
        InventoryLedger entry = new InventoryLedger();
        entry.setProductId(productId);
        entry.setLocationId(locationId);
        entry.setType(type);
        entry.setChangeQty(new BigDecimal(changeQty));
        return entry;
    }

    private StockSnapshot snapshot(Long productId, Long locationId, String qty) {
        StockSnapshot snapshot = new StockSnapshot();
        snapshot.setProductId(productId);
        snapshot.setLocationId(locationId);
        snapshot.setCurrentQty(new BigDecimal(qty));
        snapshot.setAlertQty(0);
        return snapshot;
    }
}
