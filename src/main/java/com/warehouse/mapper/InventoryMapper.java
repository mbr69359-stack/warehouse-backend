package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.Inventory;
import com.warehouse.vo.InventoryChartItemVO;
import com.warehouse.vo.InventoryStatsVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

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

    @Select("<script>" +
            "SELECT COUNT(*) AS totalSkus, " +
            "IFNULL(SUM(current_qty), 0) AS totalQty, " +
            "SUM(CASE WHEN alert_qty &gt; 0 AND current_qty &lt; alert_qty THEN 1 ELSE 0 END) AS alertCount " +
            "FROM stock_snapshot" +
            "<where><if test='warehouseId != null'>location_id = #{warehouseId}</if></where>" +
            "</script>")
    Map<String, Object> selectInventorySummary(@Param("warehouseId") Long warehouseId);

    // ── Snapshot-based read queries（流水是真相，snapshot 是缓存）────────────────

    @Select("<script>" +
            "SELECT NULL AS id, ss.location_id AS warehouse_id, ss.product_id, " +
            "ss.current_qty AS qty, ss.alert_qty, ss.updated_at AS update_time " +
            "FROM stock_snapshot ss " +
            "<where>" +
            "<if test='warehouseId != null'>ss.location_id = #{warehouseId}</if>" +
            "<if test='productId != null'> AND ss.product_id = #{productId}</if>" +
            "<if test='updatedAfter != null'> AND ss.updated_at &gt; #{updatedAfter}</if>" +
            "</where>" +
            "ORDER BY ss.updated_at DESC" +
            "</script>")
    Page<Inventory> selectPageFromSnapshot(Page<Inventory> page,
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("updatedAfter") LocalDateTime updatedAfter);

    @Select("SELECT NULL AS id, location_id AS warehouse_id, product_id, " +
            "current_qty AS qty, alert_qty, updated_at AS update_time " +
            "FROM stock_snapshot WHERE alert_qty > 0 AND current_qty < alert_qty")
    List<Inventory> selectAlertsFromSnapshot();

    @Select("SELECT IFNULL(SUM(current_qty), 0) FROM stock_snapshot")
    Long selectTotalQtyFromSnapshot();

    @Select("SELECT ss.location_id AS max_warehouse_id, w.name AS max_warehouse_name, " +
            "SUM(ss.current_qty) AS max_warehouse_qty " +
            "FROM stock_snapshot ss " +
            "JOIN warehouse w ON ss.location_id = w.id AND w.deleted = 0 " +
            "GROUP BY ss.location_id, w.name " +
            "ORDER BY SUM(ss.current_qty) DESC LIMIT 1")
    InventoryStatsVO selectMaxWarehouseFromSnapshot();

    @Select("SELECT ss.product_id, p.name AS product_name, " +
            "SUM(ss.current_qty) AS qty, MAX(ss.alert_qty) AS alert_qty " +
            "FROM stock_snapshot ss " +
            "JOIN product p ON ss.product_id = p.id AND p.deleted = 0 " +
            "GROUP BY ss.product_id, p.name " +
            "ORDER BY SUM(ss.current_qty) DESC")
    List<InventoryChartItemVO> selectChartAllFromSnapshot();

    @Select("SELECT ss.product_id, p.name AS product_name, " +
            "ss.current_qty AS qty, ss.alert_qty " +
            "FROM stock_snapshot ss " +
            "JOIN product p ON ss.product_id = p.id AND p.deleted = 0 " +
            "WHERE ss.location_id = #{warehouseId} " +
            "ORDER BY ss.current_qty DESC")
    List<InventoryChartItemVO> selectChartByWarehouseFromSnapshot(@Param("warehouseId") Long warehouseId);
}
