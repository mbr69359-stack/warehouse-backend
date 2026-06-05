package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory_ledger")
public class InventoryLedger {

    /** UUID，由调用方生成（支持离线预生成） */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 引用 product.id */
    private Long productId;

    /** 引用 warehouse.id；0 = 全局（无仓库） */
    private Long locationId;

    /** 正数=入库/入账，负数=出库/扣减 */
    private BigDecimal changeQty;

    /** inbound / outbound / adjust / transfer / transfer_in /
     *  opening / inbound_cancel / outbound_cancel / transfer_cancel */
    private String type;

    /** 关联单据号（入库单/出库单单号） */
    private String documentNo;

    /** 操作人（用户 ID 转字符串） */
    private String operator;

    private String note;

    /** 业务发生时间（UTC） */
    private LocalDateTime occurredAt;

    /** 离线设备 ID */
    private String deviceId;

    /** 1=已同步 0=离线待同步 */
    private Integer synced;

    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String productName;
}