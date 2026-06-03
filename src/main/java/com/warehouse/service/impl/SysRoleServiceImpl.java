package com.warehouse.service.impl;

import com.warehouse.dto.SysRoleDTO;
import com.warehouse.entity.SysRole;
import com.warehouse.mapper.SysRoleMapper;
import com.warehouse.common.BusinessException;
import com.warehouse.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;

    @Override
    public List<SysRole> listAll() {
        return sysRoleMapper.selectList(null);
    }

    @Override
    public void create(SysRoleDTO dto) {
        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName());
        role.setRoleCode(dto.getRoleCode());
        role.setRemark(dto.getRemark());
        sysRoleMapper.insert(role);
    }

    @Override
    public void update(SysRoleDTO dto) {
        SysRole role = sysRoleMapper.selectById(dto.getId());
        if (role == null) throw new BusinessException("角色不存在");
        role.setRoleName(dto.getRoleName());
        role.setRemark(dto.getRemark());
        sysRoleMapper.updateById(role);
    }

    @Override
    public void delete(Long id) {
        sysRoleMapper.deleteById(id);
    }
}
