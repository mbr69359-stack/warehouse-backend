package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.InOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface InOrderMapper extends BaseMapper<InOrder> {

    @Select("SELECT DATE(o.create_time) AS date, COUNT(*) AS count, " +
            "COALESCE(SUM(i.actual_qty * i.price), 0) AS amount " +
            "FROM in_order o LEFT JOIN in_order_item i ON o.id = i.order_id " +
            "WHERE o.status = 'CONFIRMED' AND o.deleted = 0 " +
            "AND o.create_time BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY DATE(o.create_time) ORDER BY date")
    List<Map<String, Object>> selectDailyReport(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);
}
