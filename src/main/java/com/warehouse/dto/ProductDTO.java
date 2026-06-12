package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ProductDTO {
    private Long id;
    @NotBlank(message = "商品名称不能为空")
    private String name;
    @NotBlank(message = "SKU编码不能为空")
    private String skuCode;
    private Long categoryId;
    @NotBlank(message = "单位不能为空")
    private String unit;
    @NotNull(message = "价格不能为空")
    private BigDecimal price;
    private BigDecimal costPrice;
    private String spec;
    private String barcode;
    private String image;
    private String remark;
    private Integer status;
    private Integer qtyPerBox;  // 每箱个数
    private BigDecimal weightPerBox;  // 每箱重量(kg)
}
