package com.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.InOrderDTO;
import com.warehouse.entity.InOrder;
import com.warehouse.entity.InOrderItem;
import com.warehouse.entity.Supplier;
import com.warehouse.entity.SysUser;
import com.warehouse.mapper.SupplierMapper;
import com.warehouse.mapper.SysUserMapper;
import com.warehouse.config.JwtUserDetails;
import com.warehouse.service.InOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Validated
@RestController
@RequestMapping("/in-orders")
@RequiredArgsConstructor
public class InOrderController {
    private final InOrderService inOrderService;
    private final SysUserMapper sysUserMapper;
    private final SupplierMapper supplierMapper;

    @GetMapping
    public Result<PageResult<InOrder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long supplierId = null;
        boolean isSupplier = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPPLIER"));
        if (isSupplier) {
            SysUser u = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, userDetails.getUsername()));
            if (u != null) {
                Supplier s = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getUserId, u.getId()));
                supplierId = s != null ? s.getId() : -1L;
            } else {
                supplierId = -1L;
            }
        }
        return Result.success(PageResult.of(inOrderService.page(current, size, status, warehouseId, supplierId)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Long> create(@RequestBody @Validated InOrderDTO dto,
                               @AuthenticationPrincipal UserDetails user) {
        return Result.success(inOrderService.create(dto, getUid(user)));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Void> confirm(@PathVariable Long id,
                                @RequestBody(required = false) List<ConfirmItemDTO> items,
                                @AuthenticationPrincipal UserDetails user) {
        inOrderService.confirm(id, items, getUid(user)); return Result.success();
    }

    @GetMapping("/{id}/items")
    public Result<List<InOrderItem>> items(@PathVariable Long id) {
        return Result.success(inOrderService.getItems(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        inOrderService.delete(id, getUid(user)); return Result.success();
    }

    private Long getUid(UserDetails user) {
        return ((JwtUserDetails) user).getUserId();
    }
}
