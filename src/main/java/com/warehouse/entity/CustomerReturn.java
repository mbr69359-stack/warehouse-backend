package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("customer_return")
public class CustomerReturn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String exchangeNo;
    private Long warehouseId;
    private String status; // DRAFT / COMPLETED
    private String remark;
    private LocalDateTime createdAt;
    private String createdBy;
    private Long outOrderId;

    @TableField(exist = false)
    private String warehouseName;
    @TableField(exist = false)
    private String outOrderNo;
}
