# 退换货流程重构（方案B：退货先入库）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复退换货创建时错误地提前生成损坏记录导致双重扣库存的 BUG，重构为"退货入库→自动生成损坏记录→补发出库→损坏核销"的完整四步流程。

**Architecture:** CustomerReturn.create() 不再自动生成 DamageRecord；改为同时创建 `CUSTOMER_RETURN_IN` 类型的入库单（草稿）和 `REPLACEMENT_OUT` 类型的出库单（草稿）。操作员确认退货入库时，系统自动为实际到货的商品创建 DamageRecord，此时货物已真实入库，之后的 DAMAGE_OUT 核销才有库存可扣。补发确认新增前置校验——必须先完成退货入库。

**Tech Stack:** Java 17 + Spring Boot 3 + MyBatis-Plus 3 + Vue 2 + Element UI

---

## 涉及文件一览

| 文件 | 动作 | 说明 |
|------|------|------|
| `src/main/resources/sql/migrate_customer_return_v2.sql` | 新建 | 数据库迁移：给 customer_return 加 in_order_id，给 damage_record 加 source/source_id |
| `entity/CustomerReturn.java` | 修改 | 新增 inOrderId、inOrderNo、inOrderStatus 字段 |
| `entity/DamageRecord.java` | 修改 | 新增 source、sourceId 字段 |
| `mapper/CustomerReturnMapper.java` | 修改 | selectPage 查询 JOIN in_order，返回入库单号和状态 |
| `service/CustomerReturnService.java` | 修改 | 新增 confirmInbound 接口方法 |
| `service/impl/CustomerReturnServiceImpl.java` | 修改（核心）| create() 重写：删除 DamageRecord 自动创建，改建 InOrder；新增 confirmInbound()；confirm() 加前置校验 |
| `controller/CustomerReturnController.java` | 修改 | 新增 POST /{id}/confirm-inbound 端点 |
| `frontend/src/api/customerReturn.js` | 修改 | 新增 confirmReturnInbound API 函数 |
| `frontend/src/views/customerReturn/Index.vue` | 修改 | 列表新增"退货入库状态"列；新增"确认退货入库"弹窗；"确认补发"改为 inOrder=CONFIRMED 时才显示 |

---

## Task 1：数据库迁移脚本

**Files:**
- Create: `src/main/resources/sql/migrate_customer_return_v2.sql`

- [ ] **Step 1: 创建迁移 SQL 文件**

```sql
-- 退换货表新增退货入库单关联
ALTER TABLE customer_return
    ADD COLUMN IF NOT EXISTS in_order_id BIGINT NULL COMMENT '关联退货入库单ID';

-- 损坏记录表新增来源追踪字段
ALTER TABLE damage_record
    ADD COLUMN IF NOT EXISTS source VARCHAR(30) NULL COMMENT '来源类型：MANUAL / CUSTOMER_RETURN',
    ADD COLUMN IF NOT EXISTS source_id BIGINT NULL COMMENT '来源单据ID（如 customer_return.id）';
```

- [ ] **Step 2: 手动在生产/开发数据库执行脚本，确认执行成功**

```bash
# 通过 Git Bash SSH 执行（参考已有部署工作流）
ssh root@139.84.247.83 "mysql -u warehouse -p warehouse < /path/to/migrate_customer_return_v2.sql"
```

预期：无报错，两张表新增对应列。

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/sql/migrate_customer_return_v2.sql
git commit -m "db: 退换货加 in_order_id，损坏记录加 source/source_id 字段"
```

---

## Task 2：实体类 + Mapper 更新

**Files:**
- Modify: `src/main/java/com/warehouse/entity/CustomerReturn.java`
- Modify: `src/main/java/com/warehouse/entity/DamageRecord.java`
- Modify: `src/main/java/com/warehouse/mapper/CustomerReturnMapper.java`

- [ ] **Step 1: 更新 CustomerReturn.java，增加入库单关联字段**

在现有字段末尾（`outOrderId` 后）增加：

```java
// 退货入库单 ID（关联 in_order 表）
private Long inOrderId;

@TableField(exist = false)
private String inOrderNo;

@TableField(exist = false)
private String inOrderStatus;
```

完整文件（整体替换）：
```java
package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("customer_return")
public class CustomerReturn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String exchangeNo;
    private Long warehouseId;
    private String status; // DRAFT / COMPLETED
    private String remark;
    private LocalDateTime createdAt;
    private String createdBy;
    private Long outOrderId;
    private Long inOrderId;

    @TableField(exist = false)
    private String warehouseName;
    @TableField(exist = false)
    private String outOrderNo;
    @TableField(exist = false)
    private String inOrderNo;
    @TableField(exist = false)
    private String inOrderStatus;
}
```

- [ ] **Step 2: 更新 DamageRecord.java，增加 source 字段**

在 `outOrderId` 字段后增加：

```java
// 来源追踪，MANUAL=手动录入，CUSTOMER_RETURN=退换货自动生成
private String source;
// 来源单据ID（customer_return.id）
private Long sourceId;
```

完整文件：
```java
package com.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("damage_record")
public class DamageRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private Long productId;
    private Integer qty;
    private String status; // PENDING / RESOLVED
    private String remark;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime resolvedAt;
    private Long outOrderId;
    private String source;    // MANUAL / CUSTOMER_RETURN
    private Long sourceId;    // customer_return.id

    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String skuCode;
    @TableField(exist = false)
    private String warehouseName;
}
```

- [ ] **Step 3: 更新 CustomerReturnMapper.java，selectPage 加 in_order JOIN**

```java
package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.CustomerReturn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerReturnMapper extends BaseMapper<CustomerReturn> {

    @Select("<script>" +
            "SELECT cr.*, w.name AS warehouse_name, " +
            "oo.order_no AS out_order_no, " +
            "io.order_no AS in_order_no, io.status AS in_order_status " +
            "FROM customer_return cr " +
            "LEFT JOIN warehouse w ON w.id = cr.warehouse_id " +
            "LEFT JOIN out_order oo ON oo.id = cr.out_order_id " +
            "LEFT JOIN in_order io ON io.id = cr.in_order_id " +
            "WHERE 1=1 " +
            "<if test='warehouseId != null'>AND cr.warehouse_id = #{warehouseId} </if>" +
            "ORDER BY cr.created_at DESC" +
            "</script>")
    Page<CustomerReturn> selectPage(Page<CustomerReturn> page, @Param("warehouseId") Long warehouseId);
}
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/warehouse/entity/CustomerReturn.java \
        src/main/java/com/warehouse/entity/DamageRecord.java \
        src/main/java/com/warehouse/mapper/CustomerReturnMapper.java
git commit -m "feat: 退换货实体加 inOrderId，损坏记录加 source 字段"
```

---

## Task 3：CustomerReturnServiceImpl.create() 重写（核心 Bug 修复）

**Files:**
- Modify: `src/main/java/com/warehouse/service/impl/CustomerReturnServiceImpl.java`

**修改点说明：**
1. **删除** 循环内自动创建 `DamageRecord` 的代码（Bug 根源）
2. **新增** 创建 `CUSTOMER_RETURN_IN` 类型入库单（关联退换商品明细）
3. **修复** OutOrder.orderNo 改用 `generateNo()` 格式，`exchangeNo` 单独存
4. 存入 `customerReturn.inOrderId`

- [ ] **Step 1: 更新 CustomerReturnServiceImpl 依赖注入，加 InOrderMapper + InOrderItemMapper**

在类顶部注入字段中增加：
```java
private final InOrderMapper inOrderMapper;
private final InOrderItemMapper inOrderItemMapper;
```

- [ ] **Step 2: 重写 create() 方法**

```java
@Override
@Transactional
public Long create(CustomerReturnDTO dto, String createdBy, Long operatorId) {
    if (dto.getItems() == null || dto.getItems().isEmpty()) {
        throw new BusinessException("退货商品明细不能为空");
    }

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String exchangeNo = "CR" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    String operator = createdBy != null ? createdBy : "";

    // 1. 创建退货入库单（草稿，等待操作员确认实际到货数量）
    InOrder inOrder = new InOrder();
    inOrder.setOrderNo(generateInNo());
    inOrder.setWarehouseId(dto.getWarehouseId());
    inOrder.setType("CUSTOMER_RETURN_IN");
    inOrder.setStatus("DRAFT");
    inOrder.setOperatorId(operatorId);
    inOrder.setRemark(dto.getRemark());
    inOrderMapper.insert(inOrder);

    for (CustomerReturnDTO.ItemDTO item : dto.getItems()) {
        InOrderItem inItem = new InOrderItem();
        inItem.setOrderId(inOrder.getId());
        inItem.setProductId(item.getProductId());
        inItem.setPlanQty(item.getQty());
        inItem.setActualQty(0); // 确认入库时填写实际数量
        inItem.setPrice(java.math.BigDecimal.ZERO);
        inOrderItemMapper.insert(inItem);
    }

    // 2. 创建补发出库单（草稿，等待退货入库确认后才能补发）
    OutOrder outOrder = new OutOrder();
    outOrder.setOrderNo(generateOutNo());     // 使用标准 OUT... 格式
    outOrder.setExchangeNo(exchangeNo);       // CR... 格式存入 exchangeNo
    outOrder.setWarehouseId(dto.getWarehouseId());
    outOrder.setType("REPLACEMENT_OUT");
    outOrder.setStatus("DRAFT");
    outOrder.setOperatorId(operatorId);
    outOrder.setRemark(dto.getRemark());
    outOrderMapper.insert(outOrder);

    for (CustomerReturnDTO.ItemDTO item : dto.getItems()) {
        OutOrderItem orderItem = new OutOrderItem();
        orderItem.setOrderId(outOrder.getId());
        orderItem.setProductId(item.getProductId());
        orderItem.setQty(item.getQty());
        orderItem.setPrice(java.math.BigDecimal.ZERO);
        outOrderItemMapper.insert(orderItem);
    }

    // 3. 创建退换货主单（关联入库单和出库单）
    CustomerReturn customerReturn = new CustomerReturn();
    customerReturn.setExchangeNo(exchangeNo);
    customerReturn.setWarehouseId(dto.getWarehouseId());
    customerReturn.setStatus("DRAFT");
    customerReturn.setRemark(dto.getRemark());
    customerReturn.setCreatedAt(now);
    customerReturn.setCreatedBy(operator);
    customerReturn.setInOrderId(inOrder.getId());
    customerReturn.setOutOrderId(outOrder.getId());
    customerReturnMapper.insert(customerReturn);

    for (CustomerReturnDTO.ItemDTO item : dto.getItems()) {
        CustomerReturnItem returnItem = new CustomerReturnItem();
        returnItem.setReturnId(customerReturn.getId());
        returnItem.setProductId(item.getProductId());
        returnItem.setQty(item.getQty());
        customerReturnItemMapper.insert(returnItem);
    }

    return customerReturn.getId();
}
```

- [ ] **Step 3: 在类内添加两个单号生成辅助方法**

注意：现有代码里没有 generateNo()，这两个方法需要新增到类里。

```java
private String generateInNo() {
    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    int rand = ThreadLocalRandom.current().nextInt(100, 1000);
    return String.format("IN%s%d", ts, rand);
}

private String generateOutNo() {
    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    int rand = ThreadLocalRandom.current().nextInt(100, 1000);
    return String.format("OUT%s%d", ts, rand);
}
```

- [ ] **Step 4: 更新文件头 import**

确保文件 import 包含：
```java
import com.warehouse.entity.InOrder;
import com.warehouse.entity.InOrderItem;
import com.warehouse.mapper.InOrderMapper;
import com.warehouse.mapper.InOrderItemMapper;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
```

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/warehouse/service/impl/CustomerReturnServiceImpl.java
git commit -m "fix: 退换货 create 不再自动生成损坏记录，改为创建退货入库单草稿"
```

---

## Task 4：新增 confirmInbound() ——确认退货入库并自动生成损坏记录

**Files:**
- Modify: `src/main/java/com/warehouse/service/CustomerReturnService.java`
- Modify: `src/main/java/com/warehouse/service/impl/CustomerReturnServiceImpl.java`

**逻辑说明：**  
操作员在退换货页面点"确认退货入库"→ 填写实际到货数量 → 调用本方法：  
1. 调用 `inOrderService.confirm()` 完成入库（货进库存）  
2. 查出实际到货明细，为每条 actualQty > 0 的商品自动创建 DamageRecord  
3. DamageRecord.source='CUSTOMER_RETURN'，sourceId=returnId（可追溯来源）

- [ ] **Step 1: 在 CustomerReturnService 接口加方法声明**

```java
void confirmInbound(Long returnId, List<ConfirmItemDTO> items, Long operatorId);
```

完整接口文件：
```java
package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ConfirmItemDTO;
import com.warehouse.dto.CustomerReturnDTO;
import com.warehouse.entity.CustomerReturn;
import com.warehouse.entity.CustomerReturnItem;
import java.util.List;

public interface CustomerReturnService {
    Page<CustomerReturn> page(int current, int size, Long warehouseId);
    Long create(CustomerReturnDTO dto, String createdBy, Long operatorId);
    List<CustomerReturnItem> listItems(Long returnId);
    void confirmInbound(Long returnId, List<ConfirmItemDTO> items, Long operatorId);
    void confirm(Long returnId, List<ConfirmItemDTO> items, Long operatorId);
}
```

- [ ] **Step 2: 在 CustomerReturnServiceImpl 中实现 confirmInbound()**

在类中新增（放在 confirm() 方法之前）：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void confirmInbound(Long returnId, List<ConfirmItemDTO> items, Long operatorId) {
    CustomerReturn ret = customerReturnMapper.selectById(returnId);
    if (ret == null) throw new BusinessException("退换货单不存在");
    if (ret.getInOrderId() == null) throw new BusinessException("该退换货单无关联入库单");

    // 检查是否已经确认过入库
    InOrder inOrder = inOrderMapper.selectById(ret.getInOrderId());
    if (inOrder == null) throw new BusinessException("关联入库单不存在");
    if ("CONFIRMED".equals(inOrder.getStatus())) throw new BusinessException("退货入库已确认，无需重复操作");

    // 1. 确认入库（货进库存，写流水）
    inOrderService.confirm(ret.getInOrderId(), items, operatorId);

    // 2. 重新加载已更新 actualQty 的明细
    List<InOrderItem> inItems = inOrderItemMapper.selectList(
            new LambdaQueryWrapper<InOrderItem>().eq(InOrderItem::getOrderId, ret.getInOrderId()));

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String operator = String.valueOf(operatorId);

    // 3. 为实际到货的每种商品自动生成损坏记录
    for (InOrderItem inItem : inItems) {
        int qty = inItem.getActualQty() != null ? inItem.getActualQty() : 0;
        if (qty <= 0) continue;

        DamageRecord damage = new DamageRecord();
        damage.setWarehouseId(ret.getWarehouseId());
        damage.setProductId(inItem.getProductId());
        damage.setQty(qty);
        damage.setStatus("PENDING");
        damage.setRemark("退换货单 " + ret.getExchangeNo() + " 自动生成");
        damage.setCreatedAt(now);
        damage.setCreatedBy(operator);
        damage.setSource("CUSTOMER_RETURN");
        damage.setSourceId(returnId);
        damageRecordMapper.insert(damage);
    }
}
```

- [ ] **Step 3: CustomerReturnServiceImpl 依赖注入中加 InOrderService**

顶部字段列表加入：
```java
private final InOrderService inOrderService;
```

注意：这里同时依赖 InOrderService 和 OutOrderService，Spring 会自动处理，不会循环依赖（InOrderService 不依赖 CustomerReturnService）。

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/warehouse/service/CustomerReturnService.java \
        src/main/java/com/warehouse/service/impl/CustomerReturnServiceImpl.java
git commit -m "feat: 退换货新增 confirmInbound，确认退货入库并自动生成损坏记录"
```

---

## Task 5：confirm() 加前置校验 + Controller 加新端点

**Files:**
- Modify: `src/main/java/com/warehouse/service/impl/CustomerReturnServiceImpl.java`
- Modify: `src/main/java/com/warehouse/controller/CustomerReturnController.java`

- [ ] **Step 1: 更新 confirm() 方法，加入退货入库完成校验**

找到现有 `confirm()` 方法，在 `if (!"DRAFT".equals(ret.getStatus()))` 检查之后，加一段：

```java
@Override
@Transactional
public void confirm(Long returnId, List<ConfirmItemDTO> items, Long operatorId) {
    CustomerReturn ret = customerReturnMapper.selectById(returnId);
    if (ret == null) throw new BusinessException("退换货单不存在");
    if (!"DRAFT".equals(ret.getStatus())) throw new BusinessException("该退换货单已完成，无需重复确认");

    // 必须先确认退货入库，否则补发出库没有意义（损坏货还没到）
    if (ret.getInOrderId() != null) {
        InOrder inOrder = inOrderMapper.selectById(ret.getInOrderId());
        if (inOrder != null && !"CONFIRMED".equals(inOrder.getStatus())) {
            throw new BusinessException("请先确认退货入库（步骤1），再执行补发出库（步骤2）");
        }
    }

    outOrderService.confirm(ret.getOutOrderId(), items, operatorId);

    ret.setStatus("COMPLETED");
    customerReturnMapper.updateById(ret);
}
```

- [ ] **Step 2: 在 CustomerReturnController 新增 confirm-inbound 端点**

在现有 `confirm` 端点之后新增：

```java
@PostMapping("/{id}/confirm-inbound")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public Result<Void> confirmInbound(@PathVariable Long id,
                                   @RequestBody(required = false) List<ConfirmItemDTO> items,
                                   @AuthenticationPrincipal UserDetails user) {
    customerReturnService.confirmInbound(id, items, ((JwtUserDetails) user).getUserId());
    return Result.success();
}
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/warehouse/service/impl/CustomerReturnServiceImpl.java \
        src/main/java/com/warehouse/controller/CustomerReturnController.java
git commit -m "feat: 补发确认前校验退货已入库；新增 confirm-inbound 接口"
```

---

## Task 6：前端 API 更新

**Files:**
- Modify: `d:\AI\warehouse-frontend\src\api\customerReturn.js`

- [ ] **Step 1: 新增 confirmReturnInbound API 函数**

完整文件替换为：
```js
import request from './request'
export const getCustomerReturns = params => request.get('/customer-returns', { params })
export const createCustomerReturn = data => request.post('/customer-returns', data)
export const getCustomerReturnItems = id => request.get(`/customer-returns/${id}/items`)
export const confirmReturnInbound = (id, items) => request.post(`/customer-returns/${id}/confirm-inbound`, items)
export const confirmCustomerReturn = (id, items) => request.post(`/customer-returns/${id}/confirm`, items)
```

- [ ] **Step 2: 提交**

```bash
git add src/api/customerReturn.js
git commit -m "feat: 前端 customerReturn API 新增 confirmReturnInbound"
```

---

## Task 7：前端 Index.vue 重构（两步流程 UI）

**Files:**
- Modify: `d:\AI\warehouse-frontend\src\views\customerReturn\Index.vue`

**UI 设计说明：**
- 列表增加"退货入库"状态标签（来自 inOrderStatus）
- 操作列：
  - `inOrderStatus == 'DRAFT'` 时显示"步骤1：确认退货入库"按钮（橙色）
  - `inOrderStatus == 'CONFIRMED' && row.status == 'DRAFT'` 时显示"步骤2：确认补发出库"按钮（绿色）
- 新增"确认退货入库"弹窗（填写实际到货数量，类似出库单确认弹窗）

- [ ] **Step 1: 更新 Index.vue 完整文件**

```vue
<template>
  <el-card>
    <div slot="header" style="display:flex;align-items:center;justify-content:space-between;">
      <span>退换货管理</span>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建退换货</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="exchangeNo" label="换货单号" width="180" show-overflow-tooltip />
      <el-table-column prop="warehouseName" label="仓库" width="120" />
      <el-table-column label="退货入库" width="110" align="center">
        <template slot-scope="{row}">
          <el-tag v-if="row.inOrderStatus === 'CONFIRMED'" type="success" size="small">已入库</el-tag>
          <el-tag v-else-if="row.inOrderNo" type="warning" size="small">待入库</el-tag>
          <span v-else style="color:#c0c4cc;">—</span>
        </template>
      </el-table-column>
      <el-table-column label="补发出库" width="110" align="center">
        <template slot-scope="{row}">
          <el-tag v-if="row.status === 'COMPLETED'" type="success" size="small">已补发</el-tag>
          <el-tag v-else type="info" size="small">待补发</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" min-width="220" align="center">
        <template slot-scope="{row}">
          <el-button size="mini" plain @click="openDetail(row)">明细</el-button>
          <el-button
            v-if="row.inOrderStatus !== 'CONFIRMED'"
            size="mini" type="warning"
            @click="openInboundConfirm(row)">
            ① 确认退货入库
          </el-button>
          <el-button
            v-if="row.inOrderStatus === 'CONFIRMED' && row.status === 'DRAFT'"
            size="mini" type="success"
            @click="openConfirm(row)">
            ② 确认补发出库
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top:16px;text-align:right;" background
      layout="total, prev, pager, next"
      :total="total" :page-size="query.size" :current-page.sync="query.current"
      @current-change="loadData" />

    <!-- 新建退换货弹窗 -->
    <el-dialog title="新建退换货" :visible.sync="createVisible" width="640px" @close="resetCreateForm">
      <el-form :model="createForm" :rules="createRules" ref="createForm" label-width="80px">
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="createForm.warehouseId" placeholder="请选择仓库" style="width:100%;">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <div style="margin:12px 0 8px;font-weight:bold;">退换商品明细</div>
      <el-button type="primary" size="small" icon="el-icon-plus" @click="addCreateItem" style="margin-bottom:10px;">
        添加商品
      </el-button>
      <el-table :data="createForm.items" border size="small">
        <el-table-column label="商品" min-width="220">
          <template slot-scope="{row}">
            <el-select v-model="row.productId" placeholder="输入名称搜索" filterable remote
              :remote-method="searchProducts" :loading="productLoading" style="width:100%;">
              <el-option v-for="p in products" :key="p.id"
                :label="p.name + '(' + p.skuCode + ')'" :value="p.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template slot-scope="{row}">
            <el-input-number v-model="row.qty" :min="1" size="small" style="width:100%;" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template slot-scope="{$index}">
            <el-button type="danger" size="mini" icon="el-icon-delete" circle
              @click="createForm.items.splice($index, 1)" />
          </template>
        </el-table-column>
      </el-table>

      <div slot="footer">
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">提交</el-button>
      </div>
    </el-dialog>

    <!-- 查看明细弹窗 -->
    <el-dialog :title="'退换货明细 — ' + (currentReturn && currentReturn.exchangeNo)"
      :visible.sync="detailVisible" width="640px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:14px;">
        <el-descriptions-item label="退货入库单">
          {{ currentReturn && currentReturn.inOrderNo || '—' }}
          <el-tag v-if="currentReturn && currentReturn.inOrderStatus === 'CONFIRMED'"
            type="success" size="mini" style="margin-left:6px;">已确认</el-tag>
          <el-tag v-else-if="currentReturn && currentReturn.inOrderNo"
            type="warning" size="mini" style="margin-left:6px;">草稿</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="补发出库单">
          {{ currentReturn && currentReturn.outOrderNo || '—' }}
          <el-tag v-if="currentReturn && currentReturn.status === 'COMPLETED'"
            type="success" size="mini" style="margin-left:6px;">已完成</el-tag>
          <el-tag v-else type="info" size="mini" style="margin-left:6px;">待处理</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-table :data="detailItems" v-loading="detailLoading" border size="small">
        <el-table-column prop="productName" label="商品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="skuCode" label="SKU" width="120" />
        <el-table-column prop="qty" label="计划数量" width="100" align="center" />
      </el-table>

      <div slot="footer">
        <el-button v-if="currentReturn && currentReturn.inOrderStatus !== 'CONFIRMED'"
          type="warning" @click="() => { detailVisible = false; openInboundConfirm(currentReturn) }">
          ① 确认退货入库
        </el-button>
        <el-button v-if="currentReturn && currentReturn.inOrderStatus === 'CONFIRMED' && currentReturn.status === 'DRAFT'"
          type="success" @click="() => { detailVisible = false; openConfirm(currentReturn) }">
          ② 确认补发出库
        </el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </div>
    </el-dialog>

    <!-- 步骤1：确认退货入库弹窗（填写实际到货数量） -->
    <el-dialog title="步骤1：确认退货入库 — 填写实际到货数量"
      :visible.sync="inboundVisible" width="560px" :close-on-click-modal="false">
      <el-alert type="info" show-icon :closable="false" style="margin-bottom:14px;"
        title="确认后系统将自动为到货商品生成损坏记录，损坏记录可在"损坏管理"页面查看和核销。" />
      <el-table :data="inboundItems" v-loading="inboundLoading" border size="small">
        <el-table-column label="商品" min-width="160">
          <template slot-scope="{row}">
            {{ row.productName || row.productId }}
            <span v-if="row.skuCode" style="color:#909399;font-size:12px;"> ({{ row.skuCode }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="planQty" label="计划数量" width="100" align="center" />
        <el-table-column label="实际到货数量" width="150">
          <template slot-scope="{row}">
            <el-input-number v-model="row.actualQty" :min="0" size="small" style="width:120px;" />
          </template>
        </el-table-column>
      </el-table>
      <div slot="footer">
        <el-button @click="inboundVisible = false">取消</el-button>
        <el-button type="warning" :loading="inboundConfirming" @click="submitInbound">确认入库</el-button>
      </div>
    </el-dialog>

    <!-- 步骤2：确认补发出库弹窗（填写实际发货数量） -->
    <el-dialog title="步骤2：确认补发出库 — 填写实际发货数量"
      :visible.sync="confirmVisible" width="560px" :close-on-click-modal="false">
      <el-table :data="confirmItems" v-loading="confirmLoading" border size="small">
        <el-table-column label="商品" min-width="160">
          <template slot-scope="{row}">
            {{ row.productName || row.productId }}
            <span v-if="row.skuCode" style="color:#909399;font-size:12px;"> ({{ row.skuCode }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="planQty" label="计划补发" width="100" align="center" />
        <el-table-column label="实际发货数量" width="150">
          <template slot-scope="{row}">
            <el-input-number v-model="row.actualQty" :min="0" size="small" style="width:120px;" />
          </template>
        </el-table-column>
      </el-table>
      <div slot="footer">
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="success" :loading="confirming" @click="submitConfirm">确认出库</el-button>
      </div>
    </el-dialog>
  </el-card>
</template>

<script>
import {
  getCustomerReturns, createCustomerReturn,
  getCustomerReturnItems, confirmReturnInbound, confirmCustomerReturn
} from '../../api/customerReturn'
import { getInOrderItems } from '../../api/inOrder'
import { getOutOrderItems } from '../../api/outOrder'
import { getWarehouses } from '../../api/warehouse'
import { getProducts } from '../../api/product'

export default {
  name: 'CustomerReturnIndex',
  data() {
    return {
      list: [], loading: false, total: 0,
      query: { current: 1, size: 20 },
      warehouses: [],
      // 新建
      createVisible: false,
      saving: false,
      createForm: { warehouseId: null, remark: '', items: [] },
      createRules: {
        warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }]
      },
      products: [], productLoading: false,
      // 明细
      detailVisible: false,
      detailLoading: false,
      detailItems: [],
      currentReturn: null,
      // 步骤1：退货入库
      inboundVisible: false,
      inboundLoading: false,
      inboundConfirming: false,
      inboundItems: [],
      // 步骤2：补发出库
      confirmVisible: false,
      confirmLoading: false,
      confirming: false,
      confirmItems: []
    }
  },
  created() {
    getWarehouses().then(r => { this.warehouses = r.data })
    this.loadData()
  },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const r = await getCustomerReturns(this.query)
        this.list = r.data.records
        this.total = r.data.total
      } finally {
        this.loading = false
      }
    },
    openCreate() {
      this.createForm = { warehouseId: null, remark: '', items: [] }
      this.products = []
      this.createVisible = true
    },
    resetCreateForm() {
      this.$refs.createForm && this.$refs.createForm.clearValidate()
    },
    addCreateItem() {
      this.createForm.items.push({ productId: null, qty: 1 })
    },
    searchProducts(query) {
      if (!query) return
      this.productLoading = true
      getProducts({ current: 1, size: 20, name: query })
        .then(r => {
          const incoming = r.data.records
          const seen = new Set(this.products.map(p => p.id))
          this.products = [...this.products, ...incoming.filter(p => !seen.has(p.id))]
        })
        .finally(() => { this.productLoading = false })
    },
    handleCreate() {
      this.$refs.createForm.validate(async valid => {
        if (!valid) return
        if (this.createForm.items.length === 0) {
          this.$message.warning('请至少添加一条退换商品')
          return
        }
        if (this.createForm.items.find(i => !i.productId)) {
          this.$message.warning('存在未选择商品的明细行，请补充或删除')
          return
        }
        this.saving = true
        try {
          await createCustomerReturn(this.createForm)
          this.$message.success('退换货单创建成功，请点击「① 确认退货入库」完成收货')
          this.createVisible = false
          this.loadData()
        } finally {
          this.saving = false
        }
      })
    },
    async openDetail(row) {
      this.currentReturn = row
      this.detailVisible = true
      this.detailLoading = true
      this.detailItems = []
      try {
        const r = await getCustomerReturnItems(row.id)
        this.detailItems = r.data || []
      } finally {
        this.detailLoading = false
      }
    },
    async openInboundConfirm(row) {
      this.currentReturn = row
      this.inboundItems = []
      this.inboundVisible = true
      this.inboundLoading = true
      try {
        const r = await getInOrderItems(row.inOrderId)
        this.inboundItems = (r.data || []).map(i => ({
          id: i.id,
          productId: i.productId,
          productName: i.productName,
          skuCode: i.skuCode,
          planQty: i.planQty,
          actualQty: i.planQty || 0
        }))
      } finally {
        this.inboundLoading = false
      }
    },
    async submitInbound() {
      if (this.inboundItems.every(i => (i.actualQty || 0) === 0)) {
        this.$message.warning('实际到货数量不能全为0')
        return
      }
      this.inboundConfirming = true
      try {
        const payload = this.inboundItems.map(i => ({ itemId: i.id, actualQty: i.actualQty || 0 }))
        await confirmReturnInbound(this.currentReturn.id, payload)
        this.$message.success('退货入库确认成功，系统已自动生成损坏记录')
        this.inboundVisible = false
        this.loadData()
      } finally {
        this.inboundConfirming = false
      }
    },
    async openConfirm(row) {
      this.currentReturn = row
      this.confirmItems = []
      this.confirmVisible = true
      this.confirmLoading = true
      try {
        const r = await getOutOrderItems(row.outOrderId)
        this.confirmItems = (r.data || []).map(i => ({
          id: i.id,
          productId: i.productId,
          productName: i.productName,
          skuCode: i.skuCode,
          planQty: i.qty,
          actualQty: i.qty
        }))
      } finally {
        this.confirmLoading = false
      }
    },
    async submitConfirm() {
      if (this.confirmItems.every(i => (i.actualQty || 0) === 0)) {
        this.$message.warning('实际发货数量不能全为0')
        return
      }
      this.confirming = true
      try {
        const payload = this.confirmItems.map(i => ({ itemId: i.id, actualQty: i.actualQty || 0 }))
        await confirmCustomerReturn(this.currentReturn.id, payload)
        this.$message.success('补发出库确认成功，退换货流程完成')
        this.confirmVisible = false
        this.loadData()
      } finally {
        this.confirming = false
      }
    }
  }
}
</script>
```

- [ ] **Step 2: 提交**

```bash
git add src/views/customerReturn/Index.vue src/api/customerReturn.js
git commit -m "feat: 退换货页面改为两步流程 UI（确认退货入库 + 确认补发出库）"
```

---

## Task 8：入库单列表显示 CUSTOMER_RETURN_IN 类型

**Files:**
- Modify: `d:\AI\warehouse-frontend\src\views\inOrder\List.vue`

入库单列表目前只显示"采购入库"和"退货入库"两种类型，需要加上 `CUSTOMER_RETURN_IN`。

- [ ] **Step 1: 修改类型显示逻辑**

找到 `inOrder/List.vue` 中类型显示的 template slot，将：
```html
{{ row.type==='PURCHASE'?'采购入库':'退货入库' }}
```
替换为：
```html
{{ { PURCHASE: '采购入库', RETURN: '退货入库', CUSTOMER_RETURN_IN: '退换货入库' }[row.type] || row.type }}
```

- [ ] **Step 2: 提交**

```bash
git add src/views/inOrder/List.vue
git commit -m "feat: 入库单列表展示退换货入库类型"
```

---

## 自检清单（实施前核对）

- [ ] Task 1 执行后：`SHOW COLUMNS FROM customer_return` 能看到 `in_order_id` 列
- [ ] Task 1 执行后：`SHOW COLUMNS FROM damage_record` 能看到 `source`、`source_id` 列
- [ ] Task 3 执行后：新建退换货单，数据库中 `customer_return.in_order_id` 不为 null，`damage_record` 表**没有**新增记录
- [ ] Task 4 执行后：确认退货入库，`in_order.status = 'CONFIRMED'`，且 `damage_record` 表**有**新增记录，`source = 'CUSTOMER_RETURN'`
- [ ] Task 5 执行后：在退货入库未确认时点"确认补发"，后端返回 400 错误，提示"请先确认退货入库"
- [ ] 全流程跑通后：损坏管理页面可看到退换货产生的损坏记录，可正常核销，库存变动正确

---

## 流程完成后的库存账面验证

执行完整流程一次，验证以下等式成立（以 A 商品在 W 仓库为例）：

```
初始库存(Q)
  + 退货入库(+m)    ← confirmInbound，类型 return_in
  - 补发出库(-n)    ← confirm，类型 replacement_out
  - 损坏核销(-m)    ← 损坏管理→DAMAGE_OUT，类型 damage_out
= Q - n             ← 净效果：只减少了补发出去的那批好货
```

如果退货数量 m = 补发数量 n，则 净效果 = Q（库存不变），符合"坏货进来、好货出去、坏货核销"的逻辑。