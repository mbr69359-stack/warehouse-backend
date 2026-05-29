package com.warehouse.dto;

import lombok.Data;
import java.util.List;

@Data
public class LoginResponse {
    private String token;
    private String username;
    private String realName;
    private List<String> roles;
}
