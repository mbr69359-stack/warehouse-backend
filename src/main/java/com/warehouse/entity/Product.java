package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    @TableField(fill = FieldFill.INSERT)
    private String uuid;
    private String skuCode;
    private Long categoryId;
    private String unit;
    private BigDecimal price;
    private BigDecimal costPrice;
    private String spec;
    private String barcode;
    private String image;
    private String remark;
    private BigDecimal weightPerBox;
    private Integer qtyPerBox;
    private Integer status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
