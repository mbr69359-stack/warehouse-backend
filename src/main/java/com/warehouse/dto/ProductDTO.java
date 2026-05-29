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
    private String image;
    private String remark;
    private Integer status;
}
