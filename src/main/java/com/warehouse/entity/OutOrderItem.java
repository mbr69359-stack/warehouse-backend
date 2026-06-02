package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("out_order_item")
public class OutOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer qty;
    private Integer actualQty;
    private BigDecimal price;
}
