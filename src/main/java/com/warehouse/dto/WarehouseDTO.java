package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class WarehouseDTO {
    private Long id;
    @NotBlank(message = "仓库名称不能为空")
    private String name;
    private String address;
    private Long managerId;
    private Integer status;
    private String remark;
}
