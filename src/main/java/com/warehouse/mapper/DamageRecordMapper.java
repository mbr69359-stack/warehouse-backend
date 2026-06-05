package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.DamageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DamageRecordMapper extends BaseMapper<DamageRecord> {

    @Select("<script>" +
            "SELECT d.*, p.name as product_name, p.sku_code, w.name as warehouse_name " +
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
            "SELECT d.*, p.name as product_name, p.sku_code, w.name as warehouse_name " +
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
}
