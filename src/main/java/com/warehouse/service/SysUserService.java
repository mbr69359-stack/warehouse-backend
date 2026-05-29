package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.SysUserDTO;
import com.warehouse.entity.SysUser;

public interface SysUserService {
    Page<SysUser> page(int current, int size, String username);
    void create(SysUserDTO dto);
    void update(SysUserDTO dto);
    void delete(Long id);
}