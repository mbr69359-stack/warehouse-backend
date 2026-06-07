package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.common.BusinessException;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.CustomerReturnDTO;
import com.warehouse.entity.*;
import com.warehouse.mapper.*;
import com.warehouse.service.CustomerReturnService;
import com.warehouse.service.OutOrderService;
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
    private final OutOrderService outOrderService;
    private final InOrderMapper inOrderMapper;
    private final InOrderItemMapper inOrderItemMapper;
    private final DamageRecordMapper damageRecordMapper;

    @Override
    public Page<CustomerReturn> page(int current, int size, Long warehouseId) {
        return customerReturnMapper.selectPage(new Page<>(current, size), warehouseId);
    }

    @Override
    @Transactional
    public Long create(CustomerReturnDTO dto, String createdBy, Long operatorId) {
        if (dto.getItems() == null || dto.getItems().isEmpty())
            throw new BusinessException("退换商品明细不能为空");

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String exchangeNo = "CR" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        String operator = createdBy != null ? createdBy : "";

        // 1. 退货入库单（草稿）—— 客户把货退回来
        InOrder inOrder = new InOrder();
        inOrder.setOrderNo("IN" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(100, 1000));
        inOrder.setWarehouseId(dto.getWarehouseId());
        inOrder.setType("RETURN_IN");
        inOrder.setStatus("DRAFT");
        inOrder.setOperatorId(operatorId);
        inOrder.setRemark(dto.getRemark());
        inOrderMapper.insert(inOrder);

        for (CustomerReturnDTO.ItemDTO item : dto.getItems()) {
            InOrderItem inItem = new InOrderItem();
            inItem.setOrderId(inOrder.getId());
            inItem.setProductId(item.getProductId());
            inItem.setPlanQty(item.getQty());
            inItem.setActualQty(0);
            inItem.setPrice(BigDecimal.ZERO);
            inOrderItemMapper.insert(inItem);
        }

        // 2. 补发出库单（草稿）—— 我们发好货给客户
        OutOrder outOrder = new OutOrder();
        outOrder.setOrderNo("OUT" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(100, 1000));
        outOrder.setExchangeNo(exchangeNo);
        outOrder.setWarehouseId(dto.getWarehouseId());
        outOrder.setType("REPLACEMENT_OUT");
        outOrder.setStatus("DRAFT");
        outOrder.setOperatorId(operatorId);
        outOrder.setRemark(dto.getRemark());
        outOrderMapper.insert(outOrder);

        for (CustomerReturnDTO.ItemDTO item : dto.getItems()) {
            OutOrderItem outItem = new OutOrderItem();
            outItem.setOrderId(outOrder.getId());
            outItem.setProductId(item.getProductId());
            outItem.setQty(item.getQty());
            outItem.setPrice(BigDecimal.ZERO);
            outOrderItemMapper.insert(outItem);
        }

        // 3. 退换货单
        CustomerReturn customerReturn = new CustomerReturn();
        customerReturn.setExchangeNo(exchangeNo);
        customerReturn.setWarehouseId(dto.getWarehouseId());
        customerReturn.setStatus("DRAFT");
        customerReturn.setRemark(dto.getRemark());
        customerReturn.setCreatedAt(now);
        customerReturn.setCreatedBy(operator);
        customerReturn.setInOrderId(inOrder.getId());
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
    @Transactional
    public void confirmInbound(Long returnId, List<ConfirmItemDTO> items, Long operatorId) {
        CustomerReturn ret = customerReturnMapper.selectByIdForUpdate(returnId);
        if (ret == null) throw new BusinessException("退换货单不存在");
        if (!"DRAFT".equals(ret.getStatus())) throw new BusinessException("该退换货单已完成");
        if (ret.getInOrderId() == null) throw new BusinessException("退货入库单不存在");

        InOrder inOrder = inOrderMapper.selectById(ret.getInOrderId());
        if (inOrder == null) throw new BusinessException("退货入库单不存在");
        if ("CONFIRMED".equals(inOrder.getStatus())) throw new BusinessException("退货已核销，请勿重复操作");

        // 更新实收数量
        List<InOrderItem> inOrderItems = inOrderItemMapper.selectList(
                new LambdaQueryWrapper<InOrderItem>().eq(InOrderItem::getOrderId, ret.getInOrderId()));
        if (items != null && !items.isEmpty()) {
            java.util.Map<Long, Integer> qtyMap = new java.util.HashMap<>();
            for (ConfirmItemDTO c : items) qtyMap.put(c.getItemId(), c.getActualQty());
            for (InOrderItem inItem : inOrderItems) {
                Integer qty = qtyMap.get(inItem.getId());
                if (qty != null) { inItem.setActualQty(qty); inOrderItemMapper.updateById(inItem); }
            }
            inOrderItems = inOrderItemMapper.selectList(
                    new LambdaQueryWrapper<InOrderItem>().eq(InOrderItem::getOrderId, ret.getInOrderId()));
        }

        // 退货不入库：直接核销，跳过库存增加
        inOrder.setStatus("CONFIRMED");
        inOrder.setConfirmTime(LocalDateTime.now(ZoneOffset.UTC));
        inOrderMapper.updateById(inOrder);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String operator = operatorId != null ? String.valueOf(operatorId) : "system";
        for (InOrderItem inItem : inOrderItems) {
            int qty = inItem.getActualQty() != null ? inItem.getActualQty() : 0;
            if (qty <= 0) continue;

            DamageRecord damage = new DamageRecord();
            damage.setWarehouseId(ret.getWarehouseId());
            damage.setProductId(inItem.getProductId());
            damage.setQty(qty);
            damage.setStatus("RESOLVED"); // 直接核销，无需再走 DAMAGE_OUT 流程
            damage.setSource("RETURN_INBOUND");
            damage.setSourceId(returnId);
            damage.setCreatedAt(now);
            damage.setCreatedBy(operator);
            damage.setResolvedAt(now);
            damageRecordMapper.insert(damage);
        }

        // 标记退货核销已完成，等待下一步补发出库
        ret.setStatus("INBOUND_DONE");
        customerReturnMapper.updateById(ret);
    }

    @Override
    @Transactional
    public void confirm(Long returnId, List<ConfirmItemDTO> items, Long operatorId) {
        CustomerReturn ret = customerReturnMapper.selectById(returnId);
        if (ret == null) throw new BusinessException("退换货单不存在");
        if ("COMPLETED".equals(ret.getStatus())) throw new BusinessException("该退换货单已完成，无需重复确认");
        if (!"INBOUND_DONE".equals(ret.getStatus()))
            throw new BusinessException("请先完成退货入库，再确认补发出库");
        // Bug 8 fix: 补发出库单被删后 outOrderId 为 null，给出清晰提示
        if (ret.getOutOrderId() == null)
            throw new BusinessException("补发出库单已被删除，请联系管理员重新处理该退换货单");

        outOrderService.confirm(ret.getOutOrderId(), items, operatorId);

        ret.setStatus("COMPLETED");
        customerReturnMapper.updateById(ret);
    }

    @Override
    public List<CustomerReturnItem> listItems(Long returnId) {
        return customerReturnItemMapper.selectItemsWithProduct(returnId);
    }

    @Override
    public List<InOrderItem> listInOrderItems(Long returnId) {
        CustomerReturn ret = customerReturnMapper.selectById(returnId);
        if (ret == null) throw new BusinessException("退换货单不存在");
        if (ret.getInOrderId() == null) throw new BusinessException("退货入库单不存在");
        return inOrderItemMapper.selectItemsWithProduct(ret.getInOrderId());
    }
}
