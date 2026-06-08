package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("warehouse")
public class Warehouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String address;
    private Long managerId;
    private Integer status;
    private String remark;
    private String type; // BOX / PIECE
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
