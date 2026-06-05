package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("customer_return_item")
public class CustomerReturnItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnId;
    private Long productId;
    private Integer qty;

    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String skuCode;
}
