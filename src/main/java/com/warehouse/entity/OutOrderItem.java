package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
    private BigDecimal costPrice;

    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String skuCode;
}
