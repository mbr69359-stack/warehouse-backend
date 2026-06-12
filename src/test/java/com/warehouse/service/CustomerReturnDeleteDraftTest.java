package com.warehouse.service;

import com.warehouse.common.BusinessException;
import com.warehouse.entity.CustomerReturn;
import com.warehouse.entity.InOrder;
import com.warehouse.entity.OutOrder;
import com.warehouse.mapper.CustomerReturnItemMapper;
import com.warehouse.mapper.CustomerReturnMapper;
import com.warehouse.mapper.DamageRecordMapper;
import com.warehouse.mapper.InOrderItemMapper;
import com.warehouse.mapper.InOrderMapper;
import com.warehouse.mapper.OutOrderItemMapper;
import com.warehouse.mapper.OutOrderMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.service.impl.CustomerReturnServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerReturnDeleteDraftTest {

    @Mock
    private CustomerReturnMapper customerReturnMapper;
    @Mock
    private CustomerReturnItemMapper customerReturnItemMapper;
    @Mock
    private OutOrderMapper outOrderMapper;
    @Mock
    private OutOrderItemMapper outOrderItemMapper;
    @Mock
    private OutOrderService outOrderService;
    @Mock
    private InOrderMapper inOrderMapper;
    @Mock
    private InOrderItemMapper inOrderItemMapper;
    @Mock
    private DamageRecordMapper damageRecordMapper;
    @Mock
    private ProductMapper productMapper;

    private CustomerReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerReturnServiceImpl(
                customerReturnMapper,
                customerReturnItemMapper,
                outOrderMapper,
                outOrderItemMapper,
                outOrderService,
                inOrderMapper,
                inOrderItemMapper,
                damageRecordMapper,
                productMapper);
    }

    @Test
    void deleteDraft_removesReturnItemsLinkedDraftOrdersAndReturn() {
        CustomerReturn ret = customerReturn("DRAFT", 10L, 20L);
        when(customerReturnMapper.selectByIdForUpdate(1L)).thenReturn(ret);
        when(inOrderMapper.selectByIdForUpdate(10L)).thenReturn(orderInStatus("DRAFT"));
        when(outOrderMapper.selectByIdForUpdate(20L)).thenReturn(orderOutStatus("DRAFT"));

        service.deleteDraft(1L, 9L);

        org.mockito.InOrder deletionOrder = org.mockito.Mockito.inOrder(
                customerReturnItemMapper,
                inOrderItemMapper,
                inOrderMapper,
                outOrderItemMapper,
                outOrderMapper,
                customerReturnMapper);
        deletionOrder.verify(customerReturnItemMapper).delete(any());
        deletionOrder.verify(inOrderItemMapper).delete(any());
        deletionOrder.verify(inOrderMapper).deleteById(10L);
        deletionOrder.verify(outOrderItemMapper).delete(any());
        deletionOrder.verify(outOrderMapper).deleteById(20L);
        deletionOrder.verify(customerReturnMapper).deleteById(1L);
        verify(inOrderMapper).selectByIdForUpdate(10L);
        verify(inOrderMapper, never()).selectById(10L);
        verify(outOrderMapper).selectByIdForUpdate(20L);
        verify(outOrderMapper, never()).selectById(20L);
    }

    @Test
    void deleteDraft_rejectsNonDraftReturnWithoutDeletingAnything() {
        when(customerReturnMapper.selectByIdForUpdate(1L))
                .thenReturn(customerReturn("INBOUND_DONE", 10L, 20L));

        assertThatThrownBy(() -> service.deleteDraft(1L, 9L))
                .isInstanceOf(BusinessException.class);

        verifyNoDeletes();
    }

    @Test
    void deleteDraft_rejectsWhenLinkedInboundOrderIsConfirmed() {
        when(customerReturnMapper.selectByIdForUpdate(1L))
                .thenReturn(customerReturn("DRAFT", 10L, 20L));
        when(inOrderMapper.selectByIdForUpdate(10L)).thenReturn(orderInStatus("CONFIRMED"));

        assertThatThrownBy(() -> service.deleteDraft(1L, 9L))
                .isInstanceOf(BusinessException.class);

        verifyNoDeletes();
    }

    @Test
    void deleteDraft_rejectsWhenLinkedOutboundOrderIsConfirmed() {
        when(customerReturnMapper.selectByIdForUpdate(1L))
                .thenReturn(customerReturn("DRAFT", 10L, 20L));
        when(inOrderMapper.selectByIdForUpdate(10L)).thenReturn(orderInStatus("DRAFT"));
        when(outOrderMapper.selectByIdForUpdate(20L)).thenReturn(orderOutStatus("CONFIRMED"));

        assertThatThrownBy(() -> service.deleteDraft(1L, 9L))
                .isInstanceOf(BusinessException.class);

        verifyNoDeletes();
    }

    private void verifyNoDeletes() {
        verify(customerReturnItemMapper, never()).delete(any());
        verify(inOrderItemMapper, never()).delete(any());
        verify(inOrderMapper, never()).deleteById(any(Serializable.class));
        verify(outOrderItemMapper, never()).delete(any());
        verify(outOrderMapper, never()).deleteById(any(Serializable.class));
        verify(customerReturnMapper, never()).deleteById(any(Serializable.class));
    }

    private CustomerReturn customerReturn(String status, Long inOrderId, Long outOrderId) {
        CustomerReturn ret = new CustomerReturn();
        ret.setId(1L);
        ret.setStatus(status);
        ret.setInOrderId(inOrderId);
        ret.setOutOrderId(outOrderId);
        return ret;
    }

    private InOrder orderInStatus(String status) {
        InOrder order = new InOrder();
        order.setId(10L);
        order.setStatus(status);
        return order;
    }

    private OutOrder orderOutStatus(String status) {
        OutOrder order = new OutOrder();
        order.setId(20L);
        order.setStatus(status);
        return order;
    }
}
