package com.warehouse.vo;

import com.warehouse.entity.Customer;
import com.warehouse.entity.OutOrder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** 客户历史订单查询返回体 */
@Data
public class CustomerOrdersVO {
    /** 客户基本信息 */
    private Customer customer;
    /** 所有已确认出库单的总消费金额 */
    private BigDecimal totalAmount;
    /** 历史出库单列表 */
    private List<OutOrder> orders;
}