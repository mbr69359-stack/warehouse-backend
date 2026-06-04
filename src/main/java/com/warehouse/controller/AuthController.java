package com.warehouse.controller;

import com.warehouse.common.BusinessException;
import com.warehouse.common.JwtUtil;
import com.warehouse.common.Result;
import com.warehouse.common.TokenBlacklistService;
import com.warehouse.dto.LoginRequest;
import com.warehouse.dto.LoginResponse;
import com.warehouse.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Validated LoginRequest request) {
        try {
            return Result.success(authService.login(request));
        } catch (BusinessException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail(401, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7);
            if (jwtUtil.validateToken(token)) {
                tokenBlacklistService.revoke(token, jwtUtil.getExpiration(token).getTime());
            }
        }
        return Result.success();
    }
}