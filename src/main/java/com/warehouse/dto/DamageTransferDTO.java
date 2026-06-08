package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class DamageTransferDTO {
    @NotNull(message = "目标仓库不能为空")
    private Long targetWarehouseId;

    @NotNull(message = "零售定价不能为空")
    @DecimalMin(value = "0.01", message = "零售价必须大于0")
    private BigDecimal transferPrice;
}