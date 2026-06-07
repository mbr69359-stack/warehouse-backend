package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.InventoryLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.warehouse.vo.LedgerExportRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface InventoryLedgerMapper extends BaseMapper<InventoryLedger> {

    /** 计算某商品在某货位的流水总量（用于重算快照时的校验） */
    @Select("SELECT IFNULL(SUM(change_qty), 0) FROM inventory_ledger " +
            "WHERE product_id = #{productId} AND location_id = #{locationId}")
    BigDecimal sumQty(@Param("productId") Long productId, @Param("locationId") Long locationId);

    @Select("<script>" +
            "SELECT DATE_FORMAT(il.occurred_at, '%Y-%m-%d %H:%i:%s') AS occurredAt, " +
            "       COALESCE(w.name, '全局') AS warehouseName, " +
            "       p.name AS productName, p.sku_code AS skuCode, p.unit, il.type AS typeName, " +
            "       il.change_qty AS changeQty, " +
            "       SUM(il.change_qty) OVER (" +
            "           PARTITION BY il.product_id, il.location_id " +
            "           ORDER BY il.occurred_at, il.created_at " +
            "           ROWS UNBOUNDED PRECEDING) AS balance, " +
            "       il.document_no AS documentNo, il.operator, il.note " +
            "FROM inventory_ledger il " +
            "JOIN product p ON p.id = il.product_id AND p.deleted = 0 " +
            "LEFT JOIN warehouse w ON w.id = il.location_id " +
            "<where>" +
            "<if test='productId != null'>il.product_id = #{productId} </if>" +
            "<if test='locationId != null'> AND il.location_id = #{locationId} </if>" +
            "<if test='type != null and type != \"\"'> AND il.type = #{type} </if>" +
            "<if test='startDate != null and startDate != \"\"'> AND il.occurred_at &gt;= #{startDate} </if>" +
            "<if test='endDate != null and endDate != \"\"'> AND il.occurred_at &lt;= CONCAT(#{endDate}, ' 23:59:59') </if>" +
            "</where>" +
            "ORDER BY il.occurred_at DESC " +
            "LIMIT 10000" +
            "</script>")
    List<LedgerExportRow> selectForExport(
            @Param("productId") Long productId,
            @Param("locationId") Long locationId,
            @Param("type") String type,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    @Select("<script>" +
            "SELECT DATE_FORMAT(il.occurred_at, '%Y-%m-%d %H:%i') AS occurredAt, il.type, " +
            "       p.name AS productName, p.sku_code AS skuCode, p.unit, " +
            "       il.change_qty AS changeQty, il.document_no AS documentNo, " +
            "       il.operator, il.note, " +
            "       COALESCE(w.name, '全局') AS warehouseName " +
            "FROM inventory_ledger il " +
            "JOIN product p ON p.id = il.product_id AND p.deleted = 0 " +
            "LEFT JOIN warehouse w ON w.id = il.location_id " +
            "WHERE il.occurred_at BETWEEN #{startDate} AND #{endDate} " +
            "<if test='type != null and type != \"\"'>AND il.type = #{type} </if>" +
            "<if test='warehouseId != null'>AND il.location_id = #{warehouseId} </if>" +
            "ORDER BY il.occurred_at DESC " +
            "LIMIT 5000" +
            "</script>")
    List<Map<String, Object>> selectLedgerReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("type") String type,
            @Param("warehouseId") Long warehouseId);
}