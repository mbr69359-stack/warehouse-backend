package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.CustomerReturnItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CustomerReturnItemMapper extends BaseMapper<CustomerReturnItem> {

    @Select("SELECT cri.*, p.name AS product_name, p.sku_code " +
            "FROM customer_return_item cri " +
            "LEFT JOIN product p ON p.id = cri.product_id " +
            "WHERE cri.return_id = #{returnId}")
    List<CustomerReturnItem> selectItemsWithProduct(@Param("returnId") Long returnId);
}
