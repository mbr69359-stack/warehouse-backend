package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.OutOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface OutOrderMapper extends BaseMapper<OutOrder> {

    @Select("SELECT * FROM out_order WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    OutOrder selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT DATE(o.create_time) AS date, COUNT(*) AS count, " +
            "COALESCE(SUM(i.qty * i.price), 0) AS amount " +
            "FROM out_order o LEFT JOIN out_order_item i ON o.id = i.order_id " +
            "WHERE o.status = 'CONFIRMED' AND o.deleted = 0 " +
            "AND o.create_time BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY DATE(o.create_time) ORDER BY date")
    List<Map<String, Object>> selectDailyReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    @Select("SELECT " +
            "COALESCE(SUM(CASE WHEN DATE(o.create_time) = CURDATE() THEN COALESCE(i.actual_qty, i.qty) ELSE 0 END), 0) AS todayOutQty, " +
            "COALESCE(SUM(CASE WHEN YEAR(o.create_time) = YEAR(CURDATE()) AND MONTH(o.create_time) = MONTH(CURDATE()) THEN COALESCE(i.actual_qty, i.qty) * COALESCE(i.price, 0) ELSE 0 END), 0) AS monthSalesAmount " +
            "FROM out_order o LEFT JOIN out_order_item i ON o.id = i.order_id " +
            "WHERE o.status = 'CONFIRMED' AND o.deleted = 0")
    Map<String, Object> selectDashboardStats();

    @Select("<script>" +
            "SELECT DATE(oo.confirm_time) AS statDate, " +
            "  COALESCE(SUM(CASE WHEN oo.type = 'SALE' THEN COALESCE(oi.actual_qty,oi.qty)*oi.price ELSE 0 END),0) AS revenue, " +
            "  COALESCE(SUM(CASE WHEN oo.type = 'SALE' THEN COALESCE(oi.actual_qty,oi.qty)*p.cost_price ELSE 0 END),0) AS cogs, " +
            "  COALESCE(SUM(CASE WHEN oo.type = 'REPLACEMENT_OUT' THEN COALESCE(oi.actual_qty,oi.qty)*p.cost_price ELSE 0 END),0) AS replacementLoss, " +
            "  COALESCE(SUM(CASE WHEN oo.type = 'DAMAGE_OUT' THEN COALESCE(oi.actual_qty,oi.qty)*p.cost_price ELSE 0 END),0) AS damageLoss " +
            "FROM out_order oo " +
            "JOIN out_order_item oi ON oi.order_id = oo.id " +
            "JOIN product p ON p.id = oi.product_id AND p.deleted = 0 " +
            "WHERE oo.status = 'CONFIRMED' AND oo.deleted = 0 " +
            "  AND oo.confirm_time BETWEEN #{startDate} AND #{endDate} " +
            "<if test='warehouseId != null'>AND oo.warehouse_id = #{warehouseId} </if>" +
            "GROUP BY DATE(oo.confirm_time) ORDER BY statDate" +
            "</script>")
    List<Map<String, Object>> selectGrossProfitReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("warehouseId") Long warehouseId);

    @Select("<script>" +
            "SELECT c.id AS customerId, c.name AS customerName, c.contact, c.phone, " +
            "       COUNT(DISTINCT oo.id) AS orderCount, " +
            "       COALESCE(SUM(oi.actual_qty), 0) AS totalQty, " +
            "       COALESCE(SUM(oi.actual_qty * oi.price), 0) AS totalAmount " +
            "FROM customer c " +
            "JOIN out_order oo ON oo.customer_id = c.id " +
            "    AND oo.status = 'CONFIRMED' AND oo.deleted = 0 " +
            "    AND oo.create_time BETWEEN #{startDate} AND #{endDate} " +
            "LEFT JOIN out_order_item oi ON oi.order_id = oo.id " +
            "WHERE c.deleted = 0 " +
            "<if test='customerId != null'>AND c.id = #{customerId} </if>" +
            "GROUP BY c.id, c.name, c.contact, c.phone " +
            "ORDER BY totalAmount DESC" +
            "</script>")
    List<Map<String, Object>> selectCustomerStatement(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("customerId") Long customerId);
}
