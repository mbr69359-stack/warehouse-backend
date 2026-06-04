package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stock_snapshot")
public class StockSnapshot {

    /** 复合主键之一：product.id */
    @TableId
    private Long productId;

    /** 复合主键之一：warehouse.id；0 = 全局 */
    private Long locationId;

    /** 由 inventory_ledger 汇总得出的当前库存 */
    private BigDecimal currentQty;

    /** 预警数量（与 inventory.alert_qty 保持同步） */
    private Integer alertQty;

    private LocalDateTime updatedAt;
}