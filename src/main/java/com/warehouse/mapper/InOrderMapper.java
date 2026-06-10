package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.InOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;


@Mapper
public interface InOrderMapper extends BaseMapper<InOrder> {

    @Select("SELECT * FROM in_order WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    InOrder selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT DATE(o.confirm_time) AS date, COUNT(DISTINCT o.id) AS count, " +
            "COALESCE(SUM(COALESCE(i.actual_qty, i.plan_qty) * i.price), 0) AS amount, " +
            "COALESCE(SUM(COALESCE(i.actual_qty, i.plan_qty) * COALESCE(p.qty_per_box, 1)), 0) AS totalQty, " +
            "COALESCE(SUM(COALESCE(i.actual_qty, i.plan_qty)), 0) AS totalBoxes " +
            "FROM in_order o " +
            "LEFT JOIN in_order_item i ON o.id = i.order_id " +
            "LEFT JOIN product p ON p.id = i.product_id AND p.deleted = 0 " +
            "WHERE o.status = 'CONFIRMED' AND o.deleted = 0 AND o.type != 'RETURN_IN' " +
            "AND o.confirm_time BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY DATE(o.confirm_time) ORDER BY date")
    List<Map<String, Object>> selectDailyReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    @Select("SELECT p.id AS productId, p.name AS productName, p.sku_code AS skuCode, p.unit, " +
            "       p.qty_per_box AS qtyPerBox, " +
            "       COALESCE(c.name, '未分类') AS categoryName, " +
            "       COALESCE(i_in.inQty, 0) * COALESCE(p.qty_per_box, 1) AS inQty, " +
            "       COALESCE(i_in.inAmount, 0) AS inAmount, " +
            "       COALESCE(i_out.outQty, 0) * COALESCE(p.qty_per_box, 1) AS outQty, " +
            "       COALESCE(i_out.outAmount, 0) AS outAmount " +
            "FROM product p " +
            "LEFT JOIN category c ON c.id = p.category_id AND c.deleted = 0 " +
            "LEFT JOIN ( " +
            "    SELECT ii.product_id, SUM(COALESCE(ii.actual_qty, ii.plan_qty)) AS inQty, " +
            "           SUM(COALESCE(ii.actual_qty, ii.plan_qty) * ii.price) AS inAmount " +
            "    FROM in_order_item ii " +
            "    JOIN in_order io ON io.id = ii.order_id " +
            "    WHERE io.status = 'CONFIRMED' AND io.deleted = 0 AND io.type != 'RETURN_IN' " +
            "      AND io.confirm_time BETWEEN #{startDate} AND #{endDate} " +
            "    GROUP BY ii.product_id " +
            ") i_in ON i_in.product_id = p.id " +
            "LEFT JOIN ( " +
            "    SELECT oi.product_id, SUM(COALESCE(oi.actual_qty, oi.qty)) AS outQty, " +
            "           SUM(COALESCE(oi.actual_qty, oi.qty) * oi.price) AS outAmount " +
            "    FROM out_order_item oi " +
            "    JOIN out_order oo ON oo.id = oi.order_id " +
            "    WHERE oo.status = 'CONFIRMED' AND oo.deleted = 0 " +
            "      AND oo.confirm_time BETWEEN #{startDate} AND #{endDate} " +
            "    GROUP BY oi.product_id " +
            ") i_out ON i_out.product_id = p.id " +
            "WHERE p.deleted = 0 " +
            "  AND (i_in.product_id IS NOT NULL OR i_out.product_id IS NOT NULL) " +
            "ORDER BY (COALESCE(i_in.inAmount, 0) + COALESCE(i_out.outAmount, 0)) DESC")
    List<Map<String, Object>> selectStockMovementReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    @Select("<script>" +
            "SELECT s.id AS supplierId, s.name AS supplierName, s.contact, s.phone, " +
            "       COUNT(DISTINCT io.id) AS orderCount, " +
            "       COALESCE(SUM(COALESCE(ii.actual_qty, ii.plan_qty)), 0) AS totalQty, " +
            "       COALESCE(SUM(COALESCE(ii.actual_qty, ii.plan_qty) * ii.price), 0) AS totalAmount " +
            "FROM supplier s " +
            "JOIN in_order io ON io.supplier_id = s.id " +
            "    AND io.status = 'CONFIRMED' AND io.deleted = 0 " +
            "    AND io.confirm_time BETWEEN #{startDate} AND #{endDate} " +
            "LEFT JOIN in_order_item ii ON ii.order_id = io.id " +
            "WHERE s.deleted = 0 " +
            "<if test='supplierId != null'>AND s.id = #{supplierId} </if>" +
            "GROUP BY s.id, s.name, s.contact, s.phone " +
            "ORDER BY totalAmount DESC" +
            "</script>")
    List<Map<String, Object>> selectSupplierStatement(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("supplierId") Long supplierId);
}