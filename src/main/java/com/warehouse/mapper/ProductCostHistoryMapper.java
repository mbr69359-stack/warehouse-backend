package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.ProductCostHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ProductCostHistoryMapper extends BaseMapper<ProductCostHistory> {

    @Select("SELECT * FROM product_cost_history WHERE product_id = #{productId} ORDER BY changed_at DESC")
    List<ProductCostHistory> selectByProductId(@Param("productId") Long productId);
}