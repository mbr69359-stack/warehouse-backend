package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OutOrderDTO {
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;
    @NotNull(message = "出库类型不能为空")
    private String type;
    private String remark;
    private List<Item> items;

    @Data
    public static class Item {
        private Long productId;
        private Integer qty;
        private BigDecimal price;
    }
}
