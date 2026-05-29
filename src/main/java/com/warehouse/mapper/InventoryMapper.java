package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Select("SELECT * FROM inventory WHERE warehouse_id = #{warehouseId} AND product_id = #{productId} FOR UPDATE")
    Inventory selectForUpdate(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    @Update("UPDATE inventory SET qty = qty + #{delta} WHERE warehouse_id = #{warehouseId} AND product_id = #{productId}")
    int updateQty(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("delta") int delta);
}
