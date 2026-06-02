package com.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SyncItemDTO {
    private Long localId;
    private String type;       // "IN" or "OUT"
    private Long warehouseId;
    private Long productId;
    private Integer qty;       // positive = IN, negative = OUT
    private String remark;
    private LocalDateTime createdAt;
}