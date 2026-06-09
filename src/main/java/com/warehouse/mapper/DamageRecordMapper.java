package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.DamageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DamageRecordMapper extends BaseMapper<DamageRecord> {

    @Select("<script>" +
            "SELECT d.*, p.name as product_name, p.sku_code, w.name as warehouse_name, " +
            "  p.cost_price as product_cost_price, p.qty_per_box as product_qty_per_box " +
            "FROM damage_record d " +
            "JOIN product p ON p.id = d.product_id " +
            "JOIN warehouse w ON w.id = d.warehouse_id " +
            "WHERE 1=1 " +
            "<if test='status != null'>AND d.status = #{status} </if>" +
            "<if test='warehouseId != null'>AND d.warehouse_id = #{warehouseId} </if>" +
            "ORDER BY d.created_at DESC" +
            "</script>")
    Page<DamageRecord> selectWithNames(Page<DamageRecord> page,
                                       @Param("status") String status,
                                       @Param("warehouseId") Long warehouseId);

    @Select("<script>" +
            "SELECT d.*, p.name as product_name, p.sku_code, w.name as warehouse_name, " +
            "  p.cost_price as product_cost_price, p.qty_per_box as product_qty_per_box " +
            "FROM damage_record d " +
            "JOIN product p ON p.id = d.product_id " +
            "JOIN warehouse w ON w.id = d.warehouse_id " +
            "WHERE d.status = 'PENDING' AND d.out_order_id IS NULL " +
            "<if test='warehouseId != null'>AND d.warehouse_id = #{warehouseId} </if>" +
            "ORDER BY d.created_at DESC" +
            "</script>")
    List<DamageRecord> selectPendingAvailable(@Param("warehouseId") Long warehouseId);

    @Select("<script>" +
            "SELECT COUNT(*) FROM damage_record " +
            "WHERE status = 'PENDING' AND out_order_id IS NULL " +
            "<if test='warehouseId != null'>AND warehouse_id = #{warehouseId} </if>" +
            "</script>")
    long countPendingAvailable(@Param("warehouseId") Long warehouseId);

    @Select("SELECT * FROM damage_record WHERE id = #{id} FOR UPDATE")
    DamageRecord selectByIdForUpdate(@Param("id") Long id);

    @Select("<script>" +
        "SELECT DATE(d.created_at) AS statDate, " +
        "  COALESCE(SUM(d.qty * COALESCE(p.cost_price, 0)), 0) AS returnDamageLoss " +
        "FROM damage_record d " +
        "JOIN product p ON p.id = d.product_id AND p.deleted = 0 " +
        "WHERE d.source = 'RETURN_INBOUND' AND d.status = 'RESOLVED' " +
        "  AND d.created_at BETWEEN #{startDate} AND #{endDate} " +
        "<if test='warehouseId != null'>AND d.warehouse_id = #{warehouseId} </if>" +
        "GROUP BY DATE(d.created_at)" +
        "</script>")
    List<Map<String, Object>> selectReturnDamageLossByDate(
        @Param("startDate") String startDate,
        @Param("endDate") String endDate,
        @Param("warehouseId") Long warehouseId);

    @Select("<script>" +
        "SELECT DATE_FORMAT(d.created_at, '%m-%d') AS date, " +
        "       DATE_FORMAT(d.created_at, '%Y-%m-%d') AS fullDate, " +
        "       p.name AS productName, p.sku_code AS skuCode, p.unit, " +
        "       w.name AS warehouseName, " +
        "       d.qty AS damagedQty, " +
        "       p.cost_price AS costPrice, " +
        "       d.cost_deduction AS costDeduction, " +
        "       d.good_qty AS goodQty, " +
        "       tw.name AS transferWarehouseName, " +
        "       d.transfer_price AS transferPrice " +
        "FROM damage_record d " +
        "JOIN product p ON p.id = d.product_id AND p.deleted = 0 " +
        "JOIN warehouse w ON w.id = d.warehouse_id " +
        "LEFT JOIN warehouse tw ON tw.id = d.transfer_warehouse_id " +
        "WHERE d.status = 'RESOLVED' " +
        "  AND d.created_at BETWEEN #{startDate} AND #{endDate} " +
        "<if test='warehouseId != null'>AND d.warehouse_id = #{warehouseId} </if>" +
        "ORDER BY d.created_at ASC" +
        "</script>")
    List<Map<String, Object>> selectDamageReport(
        @Param("startDate") String startDate,
        @Param("endDate") String endDate,
        @Param("warehouseId") Long warehouseId);
}
