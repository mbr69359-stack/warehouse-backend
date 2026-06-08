package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("expense")
public class Expense {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate expenseDate;
    private Long warehouseId;
    /**
     * 类型：UNLOADING=卸货费 DELIVERY=配送费 SALARY=员工工资
     *       COMMISSION=销售提成 STORAGE=仓储费 OTHER=其他
     */
    private String type;
    private BigDecimal amount;
    private String note;
    private Long operatorId;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}