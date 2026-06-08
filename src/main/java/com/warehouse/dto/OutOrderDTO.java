package com.warehouse.dto;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OutOrderDTO {
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;
    @NotBlank(message = "出库类型不能为空")
    private String type;
    private String remark;
    private Long targetWarehouseId;
    /** 关联客户ID（可选） */
    private Long customerId;
    /** 销售渠道：RETAIL=零售，WHOLESALE=批发（type=SALE时必填） */
    private String saleChannel;
    private List<Long> damageRecordIds;
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "商品不能为空")
        private Long productId;
        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须大于0")
        private Integer qty;
        private BigDecimal price;
    }
}
