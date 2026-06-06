package com.warehouse.controller;

import com.warehouse.common.PageResult;
import com.warehouse.common.Result;
import com.warehouse.dto.CustomerDTO;
import com.warehouse.entity.Customer;
import com.warehouse.service.CustomerService;
import com.warehouse.vo.CustomerOrdersVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

/** 客户管理 REST 接口 */
@Validated
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /** 分页查询客户列表，支持按名称模糊搜索 */
    @GetMapping
    public Result<PageResult<Customer>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name) {
        return Result.success(PageResult.of(customerService.page(current, size, name)));
    }

    /** 新建客户（需要管理员权限） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@RequestBody @Validated CustomerDTO dto) {
        customerService.create(dto);
        return Result.success();
    }

    /** 更新客户信息（需要管理员权限） */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Validated CustomerDTO dto) {
        dto.setId(id);
        customerService.update(dto);
        return Result.success();
    }

    /** 软删除客户（需要管理员权限） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.success();
    }

    /** 查询指定客户对某商品的最近一次成交价 */
    @GetMapping("/{id}/last-price")
    public Result<BigDecimal> lastPrice(@PathVariable Long id,
                                        @RequestParam Long productId) {
        return Result.success(customerService.getLastPrice(id, productId));
    }

    /** 查询客户历史出库单及总消费额 */
    @GetMapping("/{id}/orders")
    public Result<CustomerOrdersVO> orders(@PathVariable Long id) {
        return Result.success(customerService.getOrders(id));
    }
}