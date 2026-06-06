package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.common.BusinessException;
import com.warehouse.dto.CustomerDTO;
import com.warehouse.entity.Customer;
import com.warehouse.entity.OutOrder;
import com.warehouse.mapper.CustomerMapper;
import com.warehouse.mapper.OutOrderMapper;
import com.warehouse.service.CustomerService;
import com.warehouse.vo.CustomerOrdersVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;
    private final OutOrderMapper outOrderMapper;

    /** 分页查询客户，支持按名称模糊搜索，按创建时间倒序 */
    @Override
    public Page<Customer> page(int current, int size, String name) {
        LambdaQueryWrapper<Customer> q = new LambdaQueryWrapper<Customer>()
                .like(StringUtils.hasText(name), Customer::getName, name)
                .orderByDesc(Customer::getCreateTime);
        return customerMapper.selectPage(new Page<>(current, size), q);
    }

    /** 新建客户，默认状态为合作中（1） */
    @Override
    public void create(CustomerDTO dto) {
        Customer c = new Customer();
        c.setName(dto.getName());
        c.setContact(dto.getContact());
        c.setPhone(dto.getPhone());
        c.setAddress(dto.getAddress());
        c.setRemark(dto.getRemark());
        // 默认状态为合作中
        c.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        customerMapper.insert(c);
    }

    /** 更新客户信息，客户不存在时抛出业务异常 */
    @Override
    public void update(CustomerDTO dto) {
        Customer c = customerMapper.selectById(dto.getId());
        if (c == null) throw new BusinessException("客户不存在");
        c.setName(dto.getName());
        c.setContact(dto.getContact());
        c.setPhone(dto.getPhone());
        c.setAddress(dto.getAddress());
        c.setRemark(dto.getRemark());
        if (dto.getStatus() != null) c.setStatus(dto.getStatus());
        customerMapper.updateById(c);
    }

    /** 软删除客户，MyBatis-Plus @TableLogic 自动处理 */
    @Override
    public void delete(Long id) {
        customerMapper.deleteById(id);
    }

    /** 获取指定客户对某商品最近一次成交价 */
    @Override
    public BigDecimal getLastPrice(Long customerId, Long productId) {
        return customerMapper.selectLastPrice(customerId, productId);
    }

    /** 获取客户历史出库单列表及总消费额 */
    @Override
    public CustomerOrdersVO getOrders(Long customerId) {
        // 查询客户基本信息
        Customer self = customerMapper.selectById(customerId);
        // 查询该客户所有出库单，按创建时间倒序（使用字符串形式，因 customerId 字段将在 Task 5 添加）
        List<OutOrder> orders = outOrderMapper.selectList(
                new QueryWrapper<OutOrder>()
                        .eq("customer_id", customerId)
                        .orderByDesc("create_time"));
        // 计算总消费额
        BigDecimal totalAmount = customerMapper.selectTotalAmount(customerId);
        CustomerOrdersVO vo = new CustomerOrdersVO();
        vo.setCustomer(self);
        vo.setTotalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO);
        vo.setOrders(orders);
        return vo;
    }
}