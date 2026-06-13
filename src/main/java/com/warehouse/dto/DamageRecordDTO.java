package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class DamageRecordDTO {
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;
    @NotNull(message = "商品不能为空")
    private Long productId;
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    private Integer qty;
    private String remark;
    /** 登记单位：PIECE（默认）或 BOX；BOX 仅 BOX 仓可用，按每箱数换算成个 */
    private String unit;
}
