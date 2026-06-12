package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("in_order_item")
public class InOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer planQty;
    private Integer actualQty;
    private BigDecimal price;

    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String skuCode;
    @TableField(exist = false)
    private BigDecimal weightPerBox;
    @TableField(exist = false)
    private Integer qtyPerBox;
}
