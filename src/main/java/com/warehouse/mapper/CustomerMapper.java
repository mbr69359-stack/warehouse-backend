package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

    /** 查询该客户对指定商品的最近一次成交价 */
    @Select("SELECT oi.price FROM out_order_item oi " +
            "JOIN out_order o ON oi.order_id = o.id " +
            "WHERE o.customer_id = #{customerId} AND oi.product_id = #{productId} " +
            "AND o.deleted = 0 ORDER BY o.create_time DESC LIMIT 1")
    BigDecimal selectLastPrice(@Param("customerId") Long customerId,
                               @Param("productId") Long productId);

    /** 查询该客户所有已确认出库单的总消费金额 */
    @Select("SELECT COALESCE(SUM(oi.actual_qty * oi.price), 0) " +
            "FROM out_order_item oi JOIN out_order o ON oi.order_id = o.id " +
            "WHERE o.customer_id = #{customerId} AND o.status = 'CONFIRMED' AND o.deleted = 0")
    BigDecimal selectTotalAmount(@Param("customerId") Long customerId);
}