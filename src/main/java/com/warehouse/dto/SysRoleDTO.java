package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class SysRoleDTO {
    private Long id;
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;
    private String remark;
}