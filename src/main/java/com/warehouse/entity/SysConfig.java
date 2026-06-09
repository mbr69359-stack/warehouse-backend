package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_config")
public class SysConfig {
    @TableId("`key`")
    private String key;
    @TableField("`value`")
    private String value;
    private String remark;
}