package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 客户名称 */
    private String name;
    /** 联系人 */
    private String contact;
    /** 联系电话 */
    private String phone;
    /** 地址 */
    private String address;
    /** 备注 */
    private String remark;
    /** 状态：1合作中 0已停止 */
    private Integer status;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}