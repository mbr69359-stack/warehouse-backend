package com.warehouse.controller;

import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.config.JwtUserDetails;
import com.warehouse.dto.DamageRecordDTO;
import com.warehouse.entity.DamageRecord;
import com.warehouse.service.DamageRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/damage-records")
@RequiredArgsConstructor
public class DamageRecordController {

    private final DamageRecordService damageRecordService;

    @GetMapping
    public Result<PageResult<DamageRecord>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(PageResult.of(damageRecordService.page(current, size, status, warehouseId)));
    }

    @GetMapping("/pending")
    public Result<List<DamageRecord>> pending(@RequestParam(required = false) Long warehouseId) {
        return Result.success(damageRecordService.listPendingAvailable(warehouseId));
    }

    @GetMapping("/pending-count")
    public Result<Long> pendingCount(@RequestParam(required = false) Long warehouseId) {
        return Result.success(damageRecordService.countPendingAvailable(warehouseId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Long> create(@RequestBody @Validated DamageRecordDTO dto,
                               @AuthenticationPrincipal UserDetails user) {
        String username = ((JwtUserDetails) user).getUsername();
        return Result.success(damageRecordService.create(dto, username));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Void> delete(@PathVariable Long id) {
        damageRecordService.delete(id);
        return Result.success();
    }
}
