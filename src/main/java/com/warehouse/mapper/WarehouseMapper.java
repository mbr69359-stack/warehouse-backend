package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.Warehouse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {

    @Select("SELECT type FROM warehouse WHERE id = #{id}")
    String selectTypeById(@Param("id") Long id);
}
