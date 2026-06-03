package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.config.TokenStore;
import com.warehouse.dto.LoginRequest;
import com.warehouse.dto.LoginResponse;
import com.warehouse.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenStore tokenStore;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Validated LoginRequest request) {
        try {
            return Result.success(authService.login(request));
        } catch (RuntimeException e) {
            return Result.fail(401, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal UserDetails user) {
        tokenStore.remove(user.getUsername());
        return Result.success();
    }
}
