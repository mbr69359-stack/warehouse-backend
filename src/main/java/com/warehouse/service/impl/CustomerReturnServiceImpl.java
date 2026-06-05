package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.CustomerReturnDTO;
import com.warehouse.entity.*;
import com.warehouse.mapper.*;
import com.warehouse.service.CustomerReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CustomerReturnServiceImpl implements CustomerReturnService {

    private final CustomerReturnMapper customerReturnMapper;
    private final CustomerReturnItemMapper customerReturnItemMapper;
    private final OutOrderMapper outOrderMapper;
    private final OutOrderItemMapper outOrderItemMapper;
    private final DamageRecordMapper damageRecordMapper;

    @Override
    public Page<CustomerReturn> page(int current, int size, Long warehouseId) {
        return customerReturnMapper.selectPage(new Page<>(current, size), warehouseId);
    }

    @Override
    @Transactional
    public Long create(CustomerReturnDTO dto, String createdBy) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String exchangeNo = "CR" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));

        OutOrder outOrder = new OutOrder();
        outOrder.setOrderNo(exchangeNo);
        outOrder.setWarehouseId(dto.getWarehouseId());
        outOrder.setType("REPLACEMENT_OUT");
        outOrder.setStatus("DRAFT");
        outOrder.setRemark(dto.getRemark());
        outOrderMapper.insert(outOrder);

        String operator = createdBy != null ? createdBy : "";
        for (CustomerReturnDTO.ItemDTO item : dto.getItems()) {
            OutOrderItem orderItem = new OutOrderItem();
            orderItem.setOrderId(outOrder.getId());
            orderItem.setProductId(item.getProductId());
            orderItem.setQty(item.getQty());
            orderItem.setPrice(BigDecimal.ZERO);
            outOrderItemMapper.insert(orderItem);

            DamageRecord damage = new DamageRecord();
            damage.setWarehouseId(dto.getWarehouseId());
            damage.setProductId(item.getProductId());
            damage.setQty(item.getQty());
            damage.setStatus("PENDING");
            damage.setRemark(dto.getRemark());
            damage.setCreatedAt(now);
            damage.setCreatedBy(operator);
            damageRecordMapper.insert(damage);
        }

        CustomerReturn customerReturn = new CustomerReturn();
        customerReturn.setExchangeNo(exchangeNo);
        customerReturn.setWarehouseId(dto.getWarehouseId());
        customerReturn.setStatus("DRAFT");
        customerReturn.setRemark(dto.getRemark());
        customerReturn.setCreatedAt(now);
        customerReturn.setCreatedBy(operator);
        customerReturn.setOutOrderId(outOrder.getId());
        customerReturnMapper.insert(customerReturn);

        for (CustomerReturnDTO.ItemDTO item : dto.getItems()) {
            CustomerReturnItem returnItem = new CustomerReturnItem();
            returnItem.setReturnId(customerReturn.getId());
            returnItem.setProductId(item.getProductId());
            returnItem.setQty(item.getQty());
            customerReturnItemMapper.insert(returnItem);
        }

        return customerReturn.getId();
    }

    @Override
    public List<CustomerReturnItem> listItems(Long returnId) {
        return customerReturnItemMapper.selectList(
                new LambdaQueryWrapper<CustomerReturnItem>()
                        .eq(CustomerReturnItem::getReturnId, returnId));
    }
}
