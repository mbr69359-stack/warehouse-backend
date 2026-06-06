package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("out_order")
public class OutOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long warehouseId;
    private String type;
    private String status;
    private Long operatorId;
    private String remark;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private LocalDateTime confirmTime;
    private Long targetWarehouseId;
    private String exchangeNo;
    /** 关联客户ID（可选） */
    private Long customerId;
    /** 客户名称（非持久化，用于接口返回） */
    @TableField(exist = false)
    private String customerName;
}
