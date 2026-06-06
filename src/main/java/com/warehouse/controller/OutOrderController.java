package com.warehouse.controller;

import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.config.JwtUserDetails;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.OutOrderDTO;
import com.warehouse.entity.OutOrder;
import com.warehouse.entity.OutOrderItem;
import com.warehouse.service.OutOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Validated
@RestController
@RequestMapping("/out-orders")
@RequiredArgsConstructor
public class OutOrderController {
    private final OutOrderService outOrderService;

    @GetMapping("/{id}")
    public Result<OutOrder> getById(@PathVariable Long id) {
        return Result.success(outOrderService.getById(id));
    }

    @GetMapping
    public Result<PageResult<OutOrder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(PageResult.of(outOrderService.page(current, size, status, warehouseId, startDate, endDate)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Long> create(@RequestBody @Validated OutOrderDTO dto,
                               @AuthenticationPrincipal UserDetails user) {
        return Result.success(outOrderService.create(dto, getUid(user)));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Void> confirm(@PathVariable Long id,
                                @RequestBody(required = false) List<ConfirmItemDTO> items,
                                @AuthenticationPrincipal UserDetails user) {
        outOrderService.confirm(id, items, getUid(user)); return Result.success();
    }

    @GetMapping("/{id}/items")
    public Result<List<OutOrderItem>> items(@PathVariable Long id) {
        return Result.success(outOrderService.getItems(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        outOrderService.delete(id, getUid(user)); return Result.success();
    }

    private Long getUid(UserDetails user) {
        return ((JwtUserDetails) user).getUserId();
    }
}