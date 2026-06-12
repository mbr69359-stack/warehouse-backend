package com.warehouse.service;

import com.warehouse.common.BusinessException;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.entity.InOrder;
import com.warehouse.entity.InOrderItem;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.OutOrder;
import com.warehouse.entity.OutOrderItem;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderInventoryConsistencyTest {

    private static final String USED_MESSAGE = "\u8be5\u5355\u8d27\u7269\u5df2\u88ab\u4f7f\u7528";
    private static final String CONFIRMED_MESSAGE = "\u5df2\u88ab\u786e\u8ba4";
    private static final String OUT_CONFIRMED_OR_VOIDED_MESSAGE = "\u5df2\u786e\u8ba4\u6216\u5df2\u4f5c\u5e9f";
    private static final String VOIDED_MESSAGE = "\u5df2\u4f5c\u5e9f\uff0c\u4e0d\u80fd\u91cd\u590d\u64cd\u4f5c";

    @Mock
    private InOrderMapper inOrderMapper;
    @Mock
    private InOrderItemMapper inOrderItemMapper;
    @Mock
    private InventoryLedgerMapper inLedgerMapper;
    @Mock
    private StockSnapshotMapper inSnapshotMapper;
    @Mock
    private CustomerReturnMapper inCustomerReturnMapper;
    @Mock
    private DamageRecordMapper inDamageRecordMapper;
    @Mock
    private ProductMapper inProductMapper;
    @Mock
    private ProductCostHistoryMapper costHistoryMapper;
    @Mock
    private WarehouseMapper inWarehouseMapper;

    private InOrderServiceImpl inOrderService;

    @Mock
    private OutOrderMapper outOrderMapper;
    @Mock
    private OutOrderItemMapper outOrderItemMapper;
    @Mock
    private InventoryLedgerMapper outLedgerMapper;
    @Mock
    private StockSnapshotMapper outSnapshotMapper;
    @Mock
    private WarehouseMapper outWarehouseMapper;
    @Mock
    private DamageRecordMapper outDamageRecordMapper;
    @Mock
    private CustomerReturnMapper outCustomerReturnMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private ProductMapper outProductMapper;
    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private SysConfigService sysConfigService;

    private OutOrderServiceImpl outOrderService;

    @BeforeEach
    void setUp() {
        inOrderService = new InOrderServiceImpl(
                inOrderMapper,
                inOrderItemMapper,
                inLedgerMapper,
                inSnapshotMapper,
                inCustomerReturnMapper,
                inDamageRecordMapper,
                inProductMapper,
                costHistoryMapper,
                inWarehouseMapper);
        outOrderService = new OutOrderServiceImpl(
                outOrderMapper,
                outOrderItemMapper,
                outLedgerMapper,
                outSnapshotMapper,
                outWarehouseMapper,
                outDamageRecordMapper,
                outCustomerReturnMapper,
                customerMapper,
                outProductMapper,
                expenseMapper,
                sysConfigService);
    }

    @Test
    void inConfirm_actualQtyZeroSkipsInventoryIncrease() {
        Long orderId = 101L;
        InOrder order = inOrder(orderId, "DRAFT", "PURCHASE", 1L, "IN-101");
        InOrderItem item = inItem(201L, orderId, 301L, 8, null);
        ConfirmItemDTO actual = confirmItem(201L, 0);

        when(inOrderMapper.markConfirmedFromDraft(eq(orderId), any(LocalDateTime.class))).thenReturn(1);
        when(inOrderMapper.selectById(orderId)).thenReturn(order);
        when(inOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(inWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");

        inOrderService.confirm(orderId, Collections.singletonList(actual), 9L);

        verify(inSnapshotMapper, never()).selectOneForUpdate(anyLong(), anyLong());
        verify(inLedgerMapper, never()).insert(any(InventoryLedger.class));
        verify(inSnapshotMapper, never()).upsert(anyLong(), anyLong(), any(BigDecimal.class), any(Integer.class));
    }

    @Test
    void inConfirm_withoutActualItemsUsesPlanQtyEvenWhenDbActualQtyDefaultZero() {
        Long orderId = 108L;
        InOrder order = inOrder(orderId, "DRAFT", "PURCHASE", 1L, "IN-108");
        InOrderItem item = inItem(208L, orderId, 308L, 8, 0);

        when(inOrderMapper.markConfirmedFromDraft(eq(orderId), any(LocalDateTime.class))).thenReturn(1);
        when(inOrderMapper.selectById(orderId)).thenReturn(order);
        when(inOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(inWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        when(inSnapshotMapper.selectOneForUpdate(308L, 1L)).thenReturn(snapshot(308L, 1L, 0));

        inOrderService.confirm(orderId, Collections.emptyList(), 9L);

        ArgumentCaptor<InOrderItem> itemCaptor = ArgumentCaptor.forClass(InOrderItem.class);
        verify(inOrderItemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getActualQty()).isEqualTo(8);
        verify(inSnapshotMapper).upsert(308L, 1L, BigDecimal.valueOf(8), 0);
        ArgumentCaptor<InventoryLedger> ledgerCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(inLedgerMapper).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getChangeQty()).isEqualByComparingTo("8");
    }

    @Test
    void outConfirm_actualQtyZeroSkipsInventoryDeduction() {
        Long orderId = 102L;
        OutOrder order = outOrder(orderId, "DRAFT", "SALE", 1L, null, "OUT-102");
        OutOrderItem item = outItem(202L, orderId, 302L, 8, null);
        ConfirmItemDTO actual = confirmItem(202L, 0);

        when(outOrderMapper.markConfirmedFromDraft(eq(orderId), any(LocalDateTime.class))).thenReturn(1);
        when(outOrderMapper.selectById(orderId)).thenReturn(order);
        when(outWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        when(outOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        outOrderService.confirm(orderId, Collections.singletonList(actual), 9L);

        verify(outSnapshotMapper, never()).selectOneForUpdate(anyLong(), anyLong());
        verify(outLedgerMapper, never()).insert(any(InventoryLedger.class));
        verify(outSnapshotMapper, never()).upsert(anyLong(), anyLong(), any(BigDecimal.class), any(Integer.class));
    }

    @Test
    void outConfirm_withoutActualItemsUsesPlanQtyEvenWhenDbActualQtyDefaultZero() {
        Long orderId = 109L;
        OutOrder order = outOrder(orderId, "DRAFT", "SALE", 1L, null, "OUT-109");
        OutOrderItem item = outItem(209L, orderId, 309L, 8, 0);

        when(outOrderMapper.markConfirmedFromDraft(eq(orderId), any(LocalDateTime.class))).thenReturn(1);
        when(outOrderMapper.selectById(orderId)).thenReturn(order);
        when(outWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        when(outOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(outSnapshotMapper.selectOneForUpdate(309L, 1L)).thenReturn(snapshot(309L, 1L, 10));

        outOrderService.confirm(orderId, Collections.emptyList(), 9L);

        ArgumentCaptor<OutOrderItem> itemCaptor = ArgumentCaptor.forClass(OutOrderItem.class);
        verify(outOrderItemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getActualQty()).isEqualTo(8);
        verify(outSnapshotMapper).upsert(309L, 1L, BigDecimal.valueOf(2), 0);
        ArgumentCaptor<InventoryLedger> ledgerCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(outLedgerMapper).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getChangeQty()).isEqualByComparingTo("-8");
    }

    @Test
    void outDeleteConfirmedSaleRestoresActualQtyInsteadOfPlanQty() {
        Long orderId = 103L;
        OutOrder order = outOrder(orderId, "CONFIRMED", "SALE", 1L, null, "OUT-103");
        OutOrderItem item = outItem(203L, orderId, 303L, 10, 3);

        when(outOrderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        when(outOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(outWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        when(outSnapshotMapper.selectOneForUpdate(303L, 1L)).thenReturn(snapshot(303L, 1L, 7));

        outOrderService.delete(orderId, 9L);

        verify(outSnapshotMapper).upsert(303L, 1L, BigDecimal.TEN, 0);
        ArgumentCaptor<InventoryLedger> ledgerCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(outLedgerMapper).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getChangeQty()).isEqualByComparingTo("3");
    }

    @Test
    void inDeleteConfirmedOrderRollsBackActualQtyWrittenFromPlan() {
        Long orderId = 110L;
        InOrder order = inOrder(orderId, "CONFIRMED", "PURCHASE", 1L, "IN-110");
        InOrderItem item = inItem(210L, orderId, 310L, 8, 8);

        when(inOrderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        when(inOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(inWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        when(inSnapshotMapper.selectOneForUpdate(310L, 1L)).thenReturn(snapshot(310L, 1L, 8));

        inOrderService.delete(orderId, 9L);

        verify(inSnapshotMapper).upsert(310L, 1L, BigDecimal.ZERO, 0);
        ArgumentCaptor<InventoryLedger> ledgerCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(inLedgerMapper).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getChangeQty()).isEqualByComparingTo("-8");
    }

    @Test
    void outDeleteConfirmedOrderRollsBackActualQtyWrittenFromPlan() {
        Long orderId = 111L;
        OutOrder order = outOrder(orderId, "CONFIRMED", "SALE", 1L, null, "OUT-111");
        OutOrderItem item = outItem(211L, orderId, 311L, 8, 8);

        when(outOrderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        when(outOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(outWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        when(outSnapshotMapper.selectOneForUpdate(311L, 1L)).thenReturn(snapshot(311L, 1L, 2));

        outOrderService.delete(orderId, 9L);

        verify(outSnapshotMapper).upsert(311L, 1L, BigDecimal.TEN, 0);
        ArgumentCaptor<InventoryLedger> ledgerCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(outLedgerMapper).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getChangeQty()).isEqualByComparingTo("8");
    }

    @Test
    void inDeleteConfirmedOrderFailsWhenInboundWarehouseStockWasUsed() {
        Long orderId = 104L;
        InOrder order = inOrder(orderId, "CONFIRMED", "PURCHASE", 1L, "IN-104");
        InOrderItem item = inItem(204L, orderId, 304L, 10, 5);

        when(inOrderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        when(inOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(inWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        when(inSnapshotMapper.selectOneForUpdate(304L, 1L)).thenReturn(snapshot(304L, 1L, 2));

        assertThatThrownBy(() -> inOrderService.delete(orderId, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(USED_MESSAGE);

        verify(inLedgerMapper, never()).insert(any(InventoryLedger.class));
        verify(inSnapshotMapper, never()).upsert(anyLong(), anyLong(), any(BigDecimal.class), any(Integer.class));
    }

    @Test
    void outDeleteConfirmedTransferFailsBeforeRollbackWhenTargetStockWasUsed() {
        Long orderId = 105L;
        OutOrder order = outOrder(orderId, "CONFIRMED", "TRANSFER", 1L, 2L, "OUT-105");
        OutOrderItem item = outItem(205L, orderId, 305L, 10, 6);

        when(outOrderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        when(outOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(outWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        // 与 confirm() 一致的加锁顺序：先锁源仓（只锁不写），再锁目标仓做余额校验
        when(outSnapshotMapper.selectOneForUpdate(305L, 1L)).thenReturn(snapshot(305L, 1L, 0));
        when(outSnapshotMapper.selectOneForUpdate(305L, 2L)).thenReturn(snapshot(305L, 2L, 3));

        assertThatThrownBy(() -> outOrderService.delete(orderId, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(USED_MESSAGE);

        verify(outLedgerMapper, never()).insert(any(InventoryLedger.class));
        verify(outSnapshotMapper, never()).upsert(anyLong(), anyLong(), any(BigDecimal.class), any(Integer.class));
    }

    @Test
    void inConfirmAtomicDuplicateFailureDoesNotTouchInventory() {
        Long orderId = 106L;
        when(inOrderMapper.markConfirmedFromDraft(eq(orderId), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> inOrderService.confirm(orderId, Collections.emptyList(), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CONFIRMED_MESSAGE);

        verifyNoInteractions(inOrderItemMapper, inSnapshotMapper, inLedgerMapper);
    }

    @Test
    void outConfirmAtomicDuplicateFailureDoesNotTouchInventory() {
        Long orderId = 107L;
        when(outOrderMapper.markConfirmedFromDraft(eq(orderId), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> outOrderService.confirm(orderId, Collections.emptyList(), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(OUT_CONFIRMED_OR_VOIDED_MESSAGE);

        verifyNoInteractions(outOrderItemMapper, outSnapshotMapper, outLedgerMapper);
    }

    @Test
    void outDeleteConfirmedOrderMarksVoidedAndKeepsOrderAndItems() {
        Long orderId = 112L;
        OutOrder order = outOrder(orderId, "CONFIRMED", "SALE", 1L, null, "OUT-112");
        OutOrderItem item = outItem(212L, orderId, 312L, 5, 5);

        when(outOrderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        when(outOrderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(outWarehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        when(outSnapshotMapper.selectOneForUpdate(312L, 1L)).thenReturn(snapshot(312L, 1L, 0));

        outOrderService.delete(orderId, 9L);

        ArgumentCaptor<OutOrder> orderCaptor = ArgumentCaptor.forClass(OutOrder.class);
        verify(outOrderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo("VOIDED");
        verify(outOrderMapper, never()).deleteById(orderId);
        verify(outOrderItemMapper, never()).delete(any());
    }

    @Test
    void outDeleteVoidedOrderRejectedWithoutTouchingInventory() {
        Long orderId = 113L;
        OutOrder order = outOrder(orderId, "VOIDED", "SALE", 1L, null, "OUT-113");
        when(outOrderMapper.selectByIdForUpdate(orderId)).thenReturn(order);

        assertThatThrownBy(() -> outOrderService.delete(orderId, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(VOIDED_MESSAGE);

        verifyNoInteractions(outOrderItemMapper, outSnapshotMapper, outLedgerMapper);
        verify(outOrderMapper, never()).updateById(any(OutOrder.class));
        verify(outOrderMapper, never()).deleteById(orderId);
    }

    private static ConfirmItemDTO confirmItem(Long itemId, Integer actualQty) {
        ConfirmItemDTO dto = new ConfirmItemDTO();
        dto.setItemId(itemId);
        dto.setActualQty(actualQty);
        return dto;
    }

    private static InOrder inOrder(Long id, String status, String type, Long warehouseId, String orderNo) {
        InOrder order = new InOrder();
        order.setId(id);
        order.setStatus(status);
        order.setType(type);
        order.setWarehouseId(warehouseId);
        order.setOrderNo(orderNo);
        return order;
    }

    private static InOrderItem inItem(Long id, Long orderId, Long productId, Integer planQty, Integer actualQty) {
        InOrderItem item = new InOrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setPlanQty(planQty);
        item.setActualQty(actualQty);
        return item;
    }

    private static OutOrder outOrder(Long id, String status, String type, Long warehouseId, Long targetWarehouseId, String orderNo) {
        OutOrder order = new OutOrder();
        order.setId(id);
        order.setStatus(status);
        order.setType(type);
        order.setWarehouseId(warehouseId);
        order.setTargetWarehouseId(targetWarehouseId);
        order.setOrderNo(orderNo);
        return order;
    }

    private static OutOrderItem outItem(Long id, Long orderId, Long productId, Integer qty, Integer actualQty) {
        OutOrderItem item = new OutOrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQty(qty);
        item.setActualQty(actualQty);
        return item;
    }

    private static StockSnapshot snapshot(Long productId, Long locationId, int currentQty) {
        StockSnapshot snapshot = new StockSnapshot();
        snapshot.setProductId(productId);
        snapshot.setLocationId(locationId);
        snapshot.setCurrentQty(BigDecimal.valueOf(currentQty));
        snapshot.setAlertQty(0);
        return snapshot;
    }
}
