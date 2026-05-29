package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class SupplierDTO {
    private Long id;
    @NotBlank(message = "供应商名称不能为空")
    private String name;
    private String contact;
    private String phone;
    private String email;
    private String address;
    private Integer status;
    private Long userId;
}
