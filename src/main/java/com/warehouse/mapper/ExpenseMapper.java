package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.Expense;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExpenseMapper extends BaseMapper<Expense> {

    /**
     * 按日期汇总各类费用，用于净利润报表合并
     */
    @Select("<script>" +
            "SELECT expense_date AS expenseDate, " +
            "  COALESCE(SUM(CASE WHEN type='UNLOADING'  THEN amount ELSE 0 END), 0) AS unloadingFee, " +
            "  COALESCE(SUM(CASE WHEN type='DELIVERY'   THEN amount ELSE 0 END), 0) AS deliveryFee, " +
            "  COALESCE(SUM(CASE WHEN type='SALARY'     THEN amount ELSE 0 END), 0) AS salaryFee, " +
            "  COALESCE(SUM(CASE WHEN type='COMMISSION' THEN amount ELSE 0 END), 0) AS commissionFee, " +
            "  COALESCE(SUM(CASE WHEN type='STORAGE'    THEN amount ELSE 0 END), 0) AS storageFee, " +
            "  COALESCE(SUM(CASE WHEN type='OTHER'      THEN amount ELSE 0 END), 0) AS otherFee, " +
            "  COALESCE(SUM(amount), 0) AS totalExpense " +
            "FROM expense " +
            "WHERE deleted = 0 " +
            "  AND expense_date BETWEEN #{startDate} AND #{endDate} " +
            "<if test='warehouseId != null'>AND warehouse_id = #{warehouseId} </if>" +
            "GROUP BY expense_date ORDER BY expense_date" +
            "</script>")
    List<Map<String, Object>> selectExpenseSummaryByDate(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("warehouseId") Long warehouseId);
}