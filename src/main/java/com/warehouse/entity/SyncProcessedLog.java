package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_processed_log")
public class SyncProcessedLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String clientId;

    private Long localId;

    private Boolean success;

    private String rejectReason;

    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
}
