package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.warehouse.common.JwtUtil;
import com.warehouse.common.ResultCode;
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

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectByUsernameWithPwd(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException(ResultCode.UNAUTHORIZED.getMessage());
        }
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtUtil.generateToken(user.getUsername()));
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setRoles(roles);
        return resp;
    }
}
