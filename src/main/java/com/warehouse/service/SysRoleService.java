package com.warehouse.service;

import com.warehouse.dto.SysRoleDTO;
import com.warehouse.entity.SysRole;
import java.util.List;

public interface SysRoleService {
    List<SysRole> listAll();
    void create(SysRoleDTO dto);
    void update(SysRoleDTO dto);
    void delete(Long id);
}
