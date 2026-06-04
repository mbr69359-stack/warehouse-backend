package com.warehouse.service.impl;

import com.warehouse.common.BusinessException;
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
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        String token = jwtUtil.generateToken(user.getUsername());
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setRoles(roles);
        return resp;
    }
}