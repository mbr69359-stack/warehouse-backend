package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.InOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface InOrderItemMapper extends BaseMapper<InOrderItem> {

    @Select("SELECT ii.*, p.name AS product_name, p.sku_code, p.weight_per_box, p.qty_per_box " +
            "FROM in_order_item ii " +
            "LEFT JOIN product p ON p.id = ii.product_id " +
            "WHERE ii.order_id = #{orderId}")
    List<InOrderItem> selectItemsWithProduct(@Param("orderId") Long orderId);
}
