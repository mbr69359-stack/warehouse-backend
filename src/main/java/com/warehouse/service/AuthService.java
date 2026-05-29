package com.warehouse.service;

import com.warehouse.dto.LoginRequest;
import com.warehouse.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}
