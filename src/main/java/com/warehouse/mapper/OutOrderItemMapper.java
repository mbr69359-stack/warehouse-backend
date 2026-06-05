package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.OutOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface OutOrderItemMapper extends BaseMapper<OutOrderItem> {

    @Select("SELECT oi.*, p.name AS product_name, p.sku_code " +
            "FROM out_order_item oi " +
            "LEFT JOIN product p ON p.id = oi.product_id " +
            "WHERE oi.order_id = #{orderId}")
    List<OutOrderItem> selectItemsWithProductName(@Param("orderId") Long orderId);
}
