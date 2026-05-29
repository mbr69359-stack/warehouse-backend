package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class InventoryCheckDTO {
    @NotNull
    private Long warehouseId;
    private List<CheckItem> items;
    private String remark;

    @Data
    public static class CheckItem {
        private Long productId;
        private Integer actualQty;
    }
}
