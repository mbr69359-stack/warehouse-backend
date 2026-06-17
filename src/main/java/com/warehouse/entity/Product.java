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
    // 所属分类名称（非持久字段，列表/下拉用于区分同名商品）
    @TableField(exist = false)
    private String categoryName;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
