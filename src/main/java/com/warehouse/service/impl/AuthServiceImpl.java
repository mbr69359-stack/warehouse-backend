package com.warehouse.service.impl;

import com.warehouse.common.JwtUtil;
import com.warehouse.common.ResultCode;
import com.warehouse.config.TokenStore;
import com.warehouse.dto.LoginRequest;
import com.warehouse.dto.LoginResponse;
import com.warehouse.entity.SysUser;
import com.warehouse.mapper.SysUserMapper;
import com.warehouse.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectByUsernameWithPwd(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException(ResultCode.UNAUTHORIZED.getMessage());
        }
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        String token = jwtUtil.generateToken(user.getUsername());
        tokenStore.save(user.getUsername(), token);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setRoles(roles);
        return resp;
    }
}