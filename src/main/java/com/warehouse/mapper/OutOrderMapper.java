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

    @Select("SELECT DATE(o.confirm_time) AS date, COUNT(DISTINCT o.id) AS count, " +
            "COALESCE(SUM(COALESCE(i.actual_qty, i.qty) * i.price), 0) AS amount, " +
            "COALESCE(SUM(COALESCE(i.actual_qty, i.qty) * COALESCE(p.qty_per_box, 1)), 0) AS totalQty, " +
            "COALESCE(SUM(COALESCE(i.actual_qty, i.qty)), 0) AS totalBoxes " +
            "FROM out_order o " +
            "LEFT JOIN out_order_item i ON o.id = i.order_id " +
            "LEFT JOIN product p ON p.id = i.product_id AND p.deleted = 0 " +
            "WHERE o.status = 'CONFIRMED' AND o.deleted = 0 " +
            "AND o.confirm_time BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY DATE(o.confirm_time) ORDER BY date")
    List<Map<String, Object>> selectDailyReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    @Select("<script>" +
            "SELECT " +
            "COALESCE(SUM(CASE WHEN DATE(o.confirm_time) = CURDATE() THEN COALESCE(i.actual_qty, i.qty) ELSE 0 END), 0) AS todayOutQty, " +
            "COALESCE(SUM(CASE WHEN o.type = 'SALE' AND YEAR(o.confirm_time) = YEAR(CURDATE()) AND MONTH(o.confirm_time) = MONTH(CURDATE()) THEN COALESCE(i.actual_qty, i.qty) * COALESCE(i.price, 0) ELSE 0 END), 0) AS monthSalesAmount " +
            "FROM out_order o LEFT JOIN out_order_item i ON o.id = i.order_id " +
            "WHERE o.status = 'CONFIRMED' AND o.deleted = 0 " +
            "<if test='warehouseId != null'>AND o.warehouse_id = #{warehouseId} </if>" +
            "</script>")
    Map<String, Object> selectDashboardStats(@Param("warehouseId") Long warehouseId);

    @Select("<script>" +
            "SELECT DATE(oo.confirm_time) AS statDate, " +
            "  COALESCE(SUM(CASE WHEN oo.type = 'SALE' THEN COALESCE(oi.actual_qty,oi.qty)*oi.price ELSE 0 END),0) AS revenue, " +
            "  COALESCE(SUM(CASE WHEN oo.type = 'SALE' THEN COALESCE(oi.actual_qty,oi.qty)*(CASE WHEN w.type = 'BOX' THEN COALESCE(p.qty_per_box,1) ELSE 1 END)*(CASE WHEN oi.cost_price > 0 THEN oi.cost_price ELSE p.cost_price END) ELSE 0 END),0) AS cogs, " +
            "  COALESCE(SUM(CASE WHEN oo.type = 'REPLACEMENT_OUT' THEN COALESCE(oi.actual_qty,oi.qty)*(CASE WHEN oi.cost_price > 0 THEN oi.cost_price ELSE p.cost_price END) ELSE 0 END),0) AS replacementLoss, " +
            "  COALESCE(SUM(CASE WHEN oo.type = 'DAMAGE_OUT' THEN COALESCE(oi.actual_qty,oi.qty)*(CASE WHEN oi.cost_price > 0 THEN oi.cost_price ELSE p.cost_price END) ELSE 0 END),0) AS damageLoss " +
            "FROM out_order oo " +
            "JOIN out_order_item oi ON oi.order_id = oo.id " +
            "JOIN product p ON p.id = oi.product_id AND p.deleted = 0 " +
            "JOIN warehouse w ON w.id = oo.warehouse_id " +
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
            "SELECT oi.product_id AS productId, p.name AS productName, p.sku_code AS skuCode, " +
            "  COALESCE(SUM(COALESCE(oi.actual_qty,oi.qty) * oi.price), 0) AS revenue, " +
            "  COALESCE(SUM(COALESCE(oi.actual_qty,oi.qty) * (CASE WHEN w.type = 'BOX' THEN COALESCE(p.qty_per_box,1) ELSE 1 END) * COALESCE(NULLIF(oi.cost_price,0), p.cost_price, 0)), 0) AS cogs, " +
            "  COALESCE(SUM(COALESCE(oi.actual_qty,oi.qty) * oi.price), 0) - " +
            "  COALESCE(SUM(COALESCE(oi.actual_qty,oi.qty) * (CASE WHEN w.type = 'BOX' THEN COALESCE(p.qty_per_box,1) ELSE 1 END) * COALESCE(NULLIF(oi.cost_price,0), p.cost_price, 0)), 0) AS grossProfit " +
            "FROM out_order oo " +
            "JOIN out_order_item oi ON oi.order_id = oo.id " +
            "JOIN product p ON p.id = oi.product_id AND p.deleted = 0 " +
            "JOIN warehouse w ON w.id = oo.warehouse_id " +
            "WHERE oo.status = 'CONFIRMED' AND oo.deleted = 0 AND oo.type = 'SALE' " +
            "  AND oo.confirm_time BETWEEN #{startDate} AND #{endDate} " +
            "<if test='warehouseId != null'>AND oo.warehouse_id = #{warehouseId} </if>" +
            "GROUP BY oi.product_id, p.name, p.sku_code " +
            "ORDER BY grossProfit DESC" +
            "</script>")
    List<Map<String, Object>> selectProductProfitReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("warehouseId") Long warehouseId);

    @Select("<script>" +
            "SELECT c.id AS customerId, c.name AS customerName, c.contact, c.phone, " +
            "       COUNT(DISTINCT oo.id) AS orderCount, " +
            "       COALESCE(SUM(COALESCE(oi.actual_qty, oi.qty)), 0) AS totalQty, " +
            "       COALESCE(SUM(COALESCE(oi.actual_qty, oi.qty) * oi.price), 0) AS totalAmount " +
            "FROM customer c " +
            "JOIN out_order oo ON oo.customer_id = c.id " +
            "    AND oo.status = 'CONFIRMED' AND oo.deleted = 0 " +
            "    AND oo.confirm_time BETWEEN #{startDate} AND #{endDate} " +
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
