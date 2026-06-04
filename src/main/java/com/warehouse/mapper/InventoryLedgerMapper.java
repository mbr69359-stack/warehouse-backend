package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.InventoryLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;

@Mapper
public interface InventoryLedgerMapper extends BaseMapper<InventoryLedger> {

    /** 计算某商品在某货位的流水总量（用于重算快照时的校验） */
    @Select("SELECT IFNULL(SUM(change_qty), 0) FROM inventory_ledger " +
            "WHERE product_id = #{productId} AND location_id = #{locationId}")
    BigDecimal sumQty(@Param("productId") Long productId, @Param("locationId") Long locationId);
}