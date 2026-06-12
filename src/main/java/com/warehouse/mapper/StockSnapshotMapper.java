package com.warehouse.mapper;

import com.warehouse.entity.StockSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface StockSnapshotMapper {

    @Select("SELECT * FROM stock_snapshot " +
            "WHERE product_id = #{productId} AND location_id = #{locationId}")
    StockSnapshot selectOne(@Param("productId") Long productId, @Param("locationId") Long locationId);

    @Select("SELECT * FROM stock_snapshot " +
            "WHERE product_id = #{productId} AND location_id = #{locationId} FOR UPDATE")
    StockSnapshot selectOneForUpdate(@Param("productId") Long productId, @Param("locationId") Long locationId);

    /**
     * 追加流水后更新快照（绝对值 upsert）。
     * 调用方必须已持有 inventory 行锁，保证单线程写入。
     */
    @Insert("INSERT INTO stock_snapshot (product_id, location_id, current_qty, alert_qty, updated_at) " +
            "VALUES (#{productId}, #{locationId}, #{currentQty}, #{alertQty}, UTC_TIMESTAMP()) " +
            "ON DUPLICATE KEY UPDATE " +
            "  current_qty = #{currentQty}, " +
            "  alert_qty   = #{alertQty}, " +
            "  updated_at  = UTC_TIMESTAMP()")
    void upsert(@Param("productId")  Long productId,
                @Param("locationId") Long locationId,
                @Param("currentQty") BigDecimal currentQty,
                @Param("alertQty")   int alertQty);

    /** 用于 setAlertQty 接口同步更新快照中的预警值 */
    @Update("UPDATE stock_snapshot SET alert_qty = #{alertQty}, updated_at = UTC_TIMESTAMP() " +
            "WHERE product_id = #{productId} AND location_id = #{locationId}")
    int updateAlertQty(@Param("productId")  Long productId,
                       @Param("locationId") Long locationId,
                       @Param("alertQty")   int alertQty);

    /**
     * 从流水重算所有快照（账实不符时的复位手段）。
     * 注意：alert_qty 来自 inventory 表；重算后需另行同步 alert_qty。
     */
    @Insert("INSERT INTO stock_snapshot (product_id, location_id, current_qty, alert_qty, updated_at) " +
            "SELECT product_id, location_id, SUM(change_qty), 0, UTC_TIMESTAMP() " +
            "FROM inventory_ledger " +
            "GROUP BY product_id, location_id " +
            "ON DUPLICATE KEY UPDATE " +
            "  current_qty = VALUES(current_qty), " +
            "  updated_at  = UTC_TIMESTAMP()")
    void rebuildAllFromLedger();

    /** 查全部快照（用于重算后校验） */
    @Select("SELECT * FROM stock_snapshot ORDER BY product_id, location_id")
    List<StockSnapshot> selectAll();

    /** 查某商品跨所有仓位的库存总量（用于删除前校验） */
    @Select("SELECT COALESCE(SUM(current_qty), 0) FROM stock_snapshot WHERE product_id = #{productId}")
    java.math.BigDecimal selectTotalQtyByProductId(@Param("productId") Long productId);

    @Select("<script>" +
            "SELECT p.id AS productId, p.name AS productName, p.sku_code AS skuCode, " +
            "       p.spec, p.unit, p.qty_per_box AS qtyPerBox, COALESCE(c.name, '未分类') AS categoryName, " +
            "       w.name AS warehouseName, w.type AS warehouseType, ss.location_id AS locationId, " +
            "       ss.current_qty AS currentQty, ss.alert_qty AS alertQty, " +
            "       CASE WHEN ss.alert_qty > 0 AND ss.current_qty &lt; ss.alert_qty THEN 1 ELSE 0 END AS isAlert " +
            "FROM stock_snapshot ss " +
            "JOIN product p ON p.id = ss.product_id AND p.deleted = 0 " +
            "LEFT JOIN category c ON c.id = p.category_id AND c.deleted = 0 " +
            "JOIN warehouse w ON w.id = ss.location_id " +
            "<where>" +
            "<if test='warehouseId != null'>ss.location_id = #{warehouseId} </if>" +
            "</where>" +
            "ORDER BY isAlert DESC, w.name, p.name" +
            "</script>")
    List<Map<String, Object>> selectStocktakeReport(@Param("warehouseId") Long warehouseId);

    /** 查某商品在所有 BOX 仓库的快照（迁移用）*/
    @Select("SELECT ss.* FROM stock_snapshot ss " +
            "JOIN warehouse w ON w.id = ss.location_id " +
            "WHERE ss.product_id = #{productId} AND w.type = 'BOX' FOR UPDATE")
    List<StockSnapshot> selectBoxWarehouseSnapshotsForUpdate(@Param("productId") Long productId);

    /** 从 inventory 表同步 alert_qty 到快照（rebuildSnapshot 后调用） */
    @Update("UPDATE stock_snapshot s " +
            "JOIN inventory i ON i.product_id = s.product_id AND i.warehouse_id = s.location_id " +
            "SET s.alert_qty = i.alert_qty, s.updated_at = UTC_TIMESTAMP() " +
            "WHERE s.alert_qty != i.alert_qty")
    int syncAlertQtyFromInventory();

    @Select("<script>" +
            "SELECT COALESCE(SUM(ss.current_qty), 0) AS totalQty, " +
            "       COALESCE(SUM(ss.current_qty * p.cost_price), 0) AS totalValue, " +
            "       COUNT(CASE WHEN ss.alert_qty > 0 AND ss.current_qty &lt; ss.alert_qty THEN 1 END) AS alertCount, " +
            "       COUNT(DISTINCT ss.product_id) AS productCount " +
            "FROM stock_snapshot ss " +
            "JOIN product p ON p.id = ss.product_id AND p.deleted = 0 " +
            "<if test='warehouseId != null'>WHERE ss.location_id = #{warehouseId} </if>" +
            "</script>")
    Map<String, Object> selectDashboardInventoryStats(@Param("warehouseId") Long warehouseId);

    @Select("SELECT ss.location_id AS maxWarehouseId, w.name AS maxWarehouseName, " +
            "       CAST(SUM(ss.current_qty) AS SIGNED) AS maxWarehouseQty, " +
            "       CAST(SUM(FLOOR(ss.current_qty / COALESCE(p.qty_per_box, 1))) AS SIGNED) AS maxWarehouseBoxQty " +
            "FROM stock_snapshot ss " +
            "JOIN warehouse w ON w.id = ss.location_id " +
            "JOIN product p ON p.id = ss.product_id AND p.deleted = 0 " +
            "GROUP BY ss.location_id, w.name " +
            "ORDER BY SUM(ss.current_qty) DESC LIMIT 1")
    Map<String, Object> selectMaxWarehouse();

    @Select("<script>" +
            "SELECT COALESCE(SUM(FLOOR(ss.current_qty / COALESCE(p.qty_per_box, 1))), 0) AS totalBoxCount, " +
            "       COALESCE(SUM(MOD(ss.current_qty, COALESCE(p.qty_per_box, 1))), 0) AS looseCount " +
            "FROM stock_snapshot ss " +
            "JOIN product p ON p.id = ss.product_id AND p.deleted = 0 " +
            "JOIN warehouse w ON w.id = ss.location_id " +
            "<if test='warehouseId != null'>WHERE ss.location_id = #{warehouseId} </if>" +
            "</script>")
    Map<String, Object> selectTotalBoxStats(@Param("warehouseId") Long warehouseId);
}