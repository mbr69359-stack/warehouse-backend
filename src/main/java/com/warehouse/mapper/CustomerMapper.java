package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

    /** 查询该客户对指定商品的最近一次成交价 */
    @Select("SELECT oi.price FROM out_order_item oi " +
            "JOIN out_order o ON oi.order_id = o.id " +
            "WHERE o.customer_id = #{customerId} AND oi.product_id = #{productId} " +
            "AND o.deleted = 0 ORDER BY o.create_time DESC LIMIT 1")
    BigDecimal selectLastPrice(@Param("customerId") Long customerId,
                               @Param("productId") Long productId);

    /** 查询该客户所有已确认出库单的总消费金额；actual_qty=0 时回落到计划数量 */
    @Select("SELECT COALESCE(SUM(COALESCE(NULLIF(oi.actual_qty, 0), oi.qty) * oi.price), 0) " +
            "FROM out_order_item oi JOIN out_order o ON oi.order_id = o.id " +
            "WHERE o.customer_id = #{customerId} AND o.status = 'CONFIRMED' AND o.deleted = 0")
    BigDecimal selectTotalAmount(@Param("customerId") Long customerId);

    /** 按 ID 查单个客户，忽略软删标记（用于历史订单展示） */
    @Select("SELECT id, name, contact, phone, address, remark, status, deleted, create_time, update_time " +
            "FROM customer WHERE id = #{id}")
    Customer selectByIdIgnoreDeleted(@Param("id") Long id);

    /** 按 ID 列表批量查客户，忽略软删标记（用于历史订单展示） */
    @Select("<script>SELECT id, name, contact, phone, address, remark, status, deleted, create_time, update_time " +
            "FROM customer WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Customer> selectByIdsIgnoreDeleted(@Param("ids") List<Long> ids);
}