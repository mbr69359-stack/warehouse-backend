package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.dto.SysRoleDTO;
import com.warehouse.entity.SysRole;
import com.warehouse.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/sys/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping
    public Result<List<SysRole>> list() {
        return Result.success(sysRoleService.listAll());
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Validated SysRoleDTO dto) {
        sysRoleService.create(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRoleDTO dto) {
        dto.setId(id);
        sysRoleService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return Result.success();
    }
}
