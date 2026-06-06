package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.CustomerReturn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerReturnMapper extends BaseMapper<CustomerReturn> {

    @Select("SELECT * FROM customer_return WHERE id = #{id} FOR UPDATE")
    CustomerReturn selectByIdForUpdate(@Param("id") Long id);

    @Select("<script>" +
            "SELECT cr.*, w.name AS warehouse_name, oo.order_no AS out_order_no " +
            "FROM customer_return cr " +
            "LEFT JOIN warehouse w ON w.id = cr.warehouse_id " +
            "LEFT JOIN out_order oo ON oo.id = cr.out_order_id " +
            "WHERE 1=1 " +
            "<if test='warehouseId != null'>AND cr.warehouse_id = #{warehouseId} </if>" +
            "ORDER BY cr.created_at DESC" +
            "</script>")
    Page<CustomerReturn> selectPage(Page<CustomerReturn> page, @Param("warehouseId") Long warehouseId);
}
