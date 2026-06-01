package com.warehouse.vo;

import lombok.Data;

@Data
public class InventoryChartItemVO {
    private Long productId;
    private String productName;
    private Integer qty;
    private Integer alertQty;
    private Boolean isLow;
}
