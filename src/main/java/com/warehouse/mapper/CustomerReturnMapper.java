package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.CustomerReturn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerReturnMapper extends BaseMapper<CustomerReturn> {

    @Select("<script>" +
            "SELECT * FROM customer_return WHERE 1=1 " +
            "<if test='warehouseId != null'>AND warehouse_id = #{warehouseId} </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    Page<CustomerReturn> selectPage(Page<CustomerReturn> page, @Param("warehouseId") Long warehouseId);
}
