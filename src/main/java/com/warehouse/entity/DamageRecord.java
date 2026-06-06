package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("damage_record")
public class DamageRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private Long productId;
    private Integer qty;
    private String status; // PENDING / RESOLVED
    private String remark;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime resolvedAt;
    private Long outOrderId;
    private String source;   // MANUAL / RETURN_INBOUND
    private Long sourceId;

    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String skuCode;
    @TableField(exist = false)
    private String warehouseName;
}
