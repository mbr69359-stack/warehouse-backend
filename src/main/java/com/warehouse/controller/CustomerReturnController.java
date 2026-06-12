package com.warehouse.controller;

import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.config.JwtUserDetails;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.CustomerReturnDTO;
import com.warehouse.entity.CustomerReturn;
import com.warehouse.entity.CustomerReturnItem;
import com.warehouse.entity.InOrderItem;
import com.warehouse.service.CustomerReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/customer-returns")
@RequiredArgsConstructor
public class CustomerReturnController {

    private final CustomerReturnService customerReturnService;

    @GetMapping
    public Result<PageResult<CustomerReturn>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(PageResult.of(customerReturnService.page(current, size, warehouseId)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Long> create(@RequestBody @Validated CustomerReturnDTO dto,
                               @AuthenticationPrincipal UserDetails user) {
        JwtUserDetails jwtUser = (JwtUserDetails) user;
        return Result.success(customerReturnService.create(dto, jwtUser.getUsername(), jwtUser.getUserId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Void> delete(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetails user) {
        customerReturnService.deleteDraft(id, ((JwtUserDetails) user).getUserId());
        return Result.success();
    }

    // 步骤一：查看退货入库明细（含 itemId，供前端填写实际数量）
    @GetMapping("/{id}/in-order-items")
    public Result<List<InOrderItem>> inOrderItems(@PathVariable Long id) {
        return Result.success(customerReturnService.listInOrderItems(id));
    }

    // 步骤一：确认退货入库 → 库存+m，自动生成损坏记录
    @PostMapping("/{id}/confirm-inbound")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Void> confirmInbound(@PathVariable Long id,
                                       @RequestBody List<ConfirmItemDTO> items,
                                       @AuthenticationPrincipal UserDetails user) {
        customerReturnService.confirmInbound(id, items, ((JwtUserDetails) user).getUserId());
        return Result.success();
    }

    // 步骤二：确认补发出库 → 库存-n，退换货单完成
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Result<Void> confirm(@PathVariable Long id,
                                @RequestBody List<ConfirmItemDTO> items,
                                @AuthenticationPrincipal UserDetails user) {
        customerReturnService.confirm(id, items, ((JwtUserDetails) user).getUserId());
        return Result.success();
    }

    @GetMapping("/{id}/items")
    public Result<List<CustomerReturnItem>> items(@PathVariable Long id) {
        return Result.success(customerReturnService.listItems(id));
    }
}
