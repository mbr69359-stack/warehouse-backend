package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.CustomerDTO;
import com.warehouse.entity.Customer;
import com.warehouse.vo.CustomerOrdersVO;
import java.math.BigDecimal;

/** 客户管理业务接口 */
public interface CustomerService {
    /** 分页查询客户列表，支持按名称模糊搜索 */
    Page<Customer> page(int current, int size, String name);
    /** 新建客户 */
    void create(CustomerDTO dto);
    /** 更新客户信息 */
    void update(CustomerDTO dto);
    /** 软删除客户 */
    void delete(Long id);
    /** 获取指定客户对某商品的最近一次成交价 */
    BigDecimal getLastPrice(Long customerId, Long productId);
    /** 获取客户历史出库单及总消费额 */
    CustomerOrdersVO getOrders(Long customerId);
}