package com.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.dto.InOrderDTO;
import com.warehouse.entity.InOrder;
import com.warehouse.entity.InOrderItem;
import com.warehouse.entity.SysUser;
import com.warehouse.mapper.SysUserMapper;
import com.warehouse.service.InOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/in-orders")
@RequiredArgsConstructor
public class InOrderController {
    private final InOrderService inOrderService;
    private final SysUserMapper sysUserMapper;

    @GetMapping
    public Result<PageResult<InOrder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(PageResult.of(inOrderService.page(current, size, status, warehouseId)));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Validated InOrderDTO dto,
                               @AuthenticationPrincipal UserDetails user) {
        return Result.success(inOrderService.create(dto, getUid(user)));
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        inOrderService.confirm(id, getUid(user)); return Result.success();
    }

    @GetMapping("/{id}/items")
    public Result<List<InOrderItem>> items(@PathVariable Long id) {
        return Result.success(inOrderService.getItems(id));
    }

    private Long getUid(UserDetails user) {
        SysUser u = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, user.getUsername()));
        return u.getId();
    }
}
