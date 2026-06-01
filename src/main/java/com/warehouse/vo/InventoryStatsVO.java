package com.warehouse.vo;

import lombok.Data;

@Data
public class InventoryStatsVO {
    private Long totalQty;
    private String maxWarehouseName;
    private Long maxWarehouseQty;
    private Long maxWarehouseId;
}
