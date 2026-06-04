package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.Inventory;
import com.warehouse.vo.InventoryChartItemVO;
import com.warehouse.vo.InventoryStatsVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Select("SELECT * FROM inventory WHERE warehouse_id = #{warehouseId} AND product_id = #{productId} FOR UPDATE")
    Inventory selectForUpdate(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    @Update("UPDATE inventory SET qty = qty + #{delta}, version = version + 1 WHERE warehouse_id = #{warehouseId} AND product_id = #{productId}")
    int updateQty(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("delta") int delta);

    @Insert("INSERT INTO inventory (warehouse_id, product_id, qty) VALUES (#{warehouseId}, #{productId}, #{delta}) ON DUPLICATE KEY UPDATE qty = qty + #{delta}")
    void upsertQty(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("delta") int delta);

    @Select("SELECT IFNULL(SUM(qty), 0) FROM inventory")
    Long selectTotalQty();

    @Select("SELECT i.warehouse_id AS max_warehouse_id, w.name AS max_warehouse_name, " +
            "SUM(i.qty) AS max_warehouse_qty " +
            "FROM inventory i " +
            "JOIN warehouse w ON i.warehouse_id = w.id AND w.deleted = 0 " +
            "GROUP BY i.warehouse_id, w.name " +
            "ORDER BY SUM(i.qty) DESC " +
            "LIMIT 1")
    InventoryStatsVO selectMaxWarehouse();

    @Select("SELECT i.product_id AS product_id, p.name AS product_name, " +
            "SUM(i.qty) AS qty, MAX(i.alert_qty) AS alert_qty " +
            "FROM inventory i " +
            "JOIN product p ON i.product_id = p.id AND p.deleted = 0 " +
            "GROUP BY i.product_id, p.name " +
            "ORDER BY SUM(i.qty) DESC")
    List<InventoryChartItemVO> selectChartAll();

    @Select("SELECT i.product_id AS product_id, p.name AS product_name, " +
            "i.qty, i.alert_qty " +
            "FROM inventory i " +
            "JOIN product p ON i.product_id = p.id AND p.deleted = 0 " +
            "WHERE i.warehouse_id = #{warehouseId} " +
            "ORDER BY i.qty DESC")
    List<InventoryChartItemVO> selectChartByWarehouse(@Param("warehouseId") Long warehouseId);
}
