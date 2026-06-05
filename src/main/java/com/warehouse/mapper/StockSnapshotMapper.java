package com.warehouse.mapper;

import com.warehouse.entity.StockSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface StockSnapshotMapper {

    /** 行锁读取（与 inventory FOR UPDATE 配合使用，用于在同一事务内读最新快照） */
    @Select("SELECT * FROM stock_snapshot " +
            "WHERE product_id = #{productId} AND location_id = #{locationId}")
    StockSnapshot selectOne(@Param("productId") Long productId, @Param("locationId") Long locationId);

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

    /** 从 inventory 表同步 alert_qty 到快照（rebuildSnapshot 后调用） */
    @Update("UPDATE stock_snapshot s " +
            "JOIN inventory i ON i.product_id = s.product_id AND i.warehouse_id = s.location_id " +
            "SET s.alert_qty = i.alert_qty, s.updated_at = UTC_TIMESTAMP() " +
            "WHERE s.alert_qty != i.alert_qty")
    int syncAlertQtyFromInventory();
}