package com.warehouse.service;

import com.warehouse.common.BusinessException;
import com.warehouse.dto.CustomerReturnDTO;
import com.warehouse.entity.CustomerReturn;
import com.warehouse.entity.CustomerReturnItem;
import com.warehouse.entity.InOrder;
import com.warehouse.entity.InOrderItem;
import com.warehouse.entity.OutOrder;
import com.warehouse.entity.OutOrderItem;
import com.warehouse.entity.Product;
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

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerReturnUpdateTest {
    @Mock CustomerReturnMapper customerReturnMapper;
    @Mock CustomerReturnItemMapper customerReturnItemMapper;
    @Mock OutOrderMapper outOrderMapper;
    @Mock OutOrderItemMapper outOrderItemMapper;
    @Mock OutOrderService outOrderService;
    @Mock InOrderMapper inOrderMapper;
    @Mock InOrderItemMapper inOrderItemMapper;
    @Mock DamageRecordMapper damageRecordMapper;
    @Mock ProductMapper productMapper;

    CustomerReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerReturnServiceImpl(customerReturnMapper, customerReturnItemMapper,
                outOrderMapper, outOrderItemMapper, outOrderService, inOrderMapper, inOrderItemMapper,
                damageRecordMapper, productMapper);
    }

    private CustomerReturn ret(String status, Long inId, Long outId) {
        CustomerReturn r = new CustomerReturn();
        r.setId(1L); r.setStatus(status); r.setInOrderId(inId); r.setOutOrderId(outId);
        return r;
    }
    private InOrder inOrder(String status) { InOrder o = new InOrder(); o.setId(20L); o.setStatus(status); return o; }
    private OutOrder outOrder(String status) { OutOrder o = new OutOrder(); o.setId(30L); o.setStatus(status); return o; }
    private CustomerReturnDTO.ItemDTO item(Long pid, Integer qty) {
        CustomerReturnDTO.ItemDTO i = new CustomerReturnDTO.ItemDTO();
        i.setProductId(pid); i.setQty(qty); return i;
    }
    private CustomerReturnDTO dto(CustomerReturnDTO.ItemDTO... items) {
        CustomerReturnDTO d = new CustomerReturnDTO();
        d.setWarehouseId(10L); d.setItems(Arrays.asList(items));
        return d;
    }

    @Test
    void update_draft_rebuildsAllThree() {
        when(customerReturnMapper.selectByIdForUpdate(1L)).thenReturn(ret("DRAFT", 20L, 30L));
        when(inOrderMapper.selectByIdForUpdate(20L)).thenReturn(inOrder("DRAFT"));
        when(outOrderMapper.selectByIdForUpdate(30L)).thenReturn(outOrder("DRAFT"));
        Product p = new Product(); p.setId(2L); p.setCostPrice(new BigDecimal("4.00"));
        when(productMapper.selectById(any())).thenReturn(p);

        service.update(1L, dto(item(2L, 3), item(5L, 7)), 99L);

        verify(inOrderMapper).updateById(any(InOrder.class));
        verify(inOrderItemMapper).delete(any());
        verify(inOrderItemMapper, times(2)).insert(any(InOrderItem.class));
        verify(outOrderMapper).updateById(any(OutOrder.class));
        verify(outOrderItemMapper).delete(any());
        verify(outOrderItemMapper, times(2)).insert(any(OutOrderItem.class));
        verify(customerReturnMapper).updateById(any(CustomerReturn.class));
        verify(customerReturnItemMapper).delete(any());
        verify(customerReturnItemMapper, times(2)).insert(any(CustomerReturnItem.class));
    }

    @Test
    void update_notDraft_throws() {
        when(customerReturnMapper.selectByIdForUpdate(1L)).thenReturn(ret("INBOUND_DONE", 20L, 30L));
        assertThatThrownBy(() -> service.update(1L, dto(item(2L, 1)), 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("只有草稿");
    }

    @Test
    void update_inOrderNotDraft_throws() {
        when(customerReturnMapper.selectByIdForUpdate(1L)).thenReturn(ret("DRAFT", 20L, 30L));
        when(inOrderMapper.selectByIdForUpdate(20L)).thenReturn(inOrder("CONFIRMED"));
        assertThatThrownBy(() -> service.update(1L, dto(item(2L, 1)), 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("退货入库单已流转");
    }

    @Test
    void update_outOrderNotDraft_throws() {
        when(customerReturnMapper.selectByIdForUpdate(1L)).thenReturn(ret("DRAFT", 20L, 30L));
        when(inOrderMapper.selectByIdForUpdate(20L)).thenReturn(inOrder("DRAFT"));
        when(outOrderMapper.selectByIdForUpdate(30L)).thenReturn(outOrder("CONFIRMED"));
        assertThatThrownBy(() -> service.update(1L, dto(item(2L, 1)), 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("补发出库单已流转");
    }

    @Test
    void update_emptyItems_throws() {
        assertThatThrownBy(() -> service.update(1L, dto(), 99L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("明细不能为空");
    }
}
