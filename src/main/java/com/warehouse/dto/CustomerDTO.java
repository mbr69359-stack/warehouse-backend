package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class CustomerDTO {
    /** 客户ID（更新时必填） */
    private Long id;
    /** 客户名称（必填） */
    @NotBlank(message = "客户名称不能为空")
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
}