package com.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class SyncItemDTO {
    private Long localId;
    private String clientId;
    @NotBlank(message = "type cannot be blank")
    private String type;       // "IN" or "OUT"
    @NotNull(message = "warehouseId cannot be null")
    private Long warehouseId;
    @NotNull(message = "productId cannot be null")
    private Long productId;
    @NotNull(message = "qty cannot be null")
    private Integer qty;       // positive = IN, negative = OUT
    private String remark;
    private LocalDateTime createdAt;
}
