# 双单位仓库 + 财务报表改造设计文档

**日期：** 2026-06-09  
**系统：** Vue + SpringBoot + MySQL 陶瓷仓储管理系统  
**状态：** 已确认，待实施

---

## 背景与目标

### 现有痛点
1. 库存按箱存储，退货/破损按个计算，单位混乱
2. 破损后剩余好货没有自动调拨零售仓的流程
3. 首页仓库固定，无法切换查看
4. 缺少破损损耗报表，现有报表没有箱/个双视图

### 目标
1. 仓库分类：BOX（按箱）/ PIECE（按个）
2. 底层统一存「个」，前端换算展示箱数
3. 破损 → 成本核销 → 好货调拨零售仓 → 重新定价，一套完整流程
4. 首页可切换仓库，库存视图支持箱/个切换
5. 新增破损损耗报表，现有报表适配双单位

---

## 核心原则

- **底层统一存「个」**：`inventory.qty` 全部以「个」为单位，前端按需换算
- **渐进式迁移**：`qtyPerBox` 未填的商品先以 `qty_unit='BOX'` 兼容旧数据，填好后自动触发迁移
- **不破坏现有数据**：所有改动为新增字段，迁移前先备份，历史流水账保留
- **财务闭环**：入库成本 = 破损核销 + 调拨成本 + 已售成本 + 剩余库存价值

---

## 第一段：数据库改动

### 1. `warehouse` 表 — 新增仓库类型

```sql
ALTER TABLE warehouse 
ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'BOX' 
COMMENT 'BOX=按箱仓库, PIECE=按个仓库';
```

现有仓库默认 `BOX`，在仓库设置页手动修改。

### 2. `inventory` 表 — 新增单位标记

```sql
ALTER TABLE inventory 
ADD COLUMN qty_unit VARCHAR(10) NOT NULL DEFAULT 'BOX'
COMMENT 'BOX=qty是箱数(待迁移), PIECE=qty是个数(已迁移)';
```

### 3. 数据迁移（按商品逐步触发）

```sql
-- 迁移前备份
CREATE TABLE inventory_backup_20260609 AS SELECT * FROM inventory;

-- 单个商品迁移（在 ProductService.updateQtyPerBox() 中触发）
UPDATE inventory i
JOIN warehouse w ON i.warehouse_id = w.id
SET i.qty = i.qty * :qtyPerBox,
    i.qty_unit = 'PIECE'
WHERE i.product_id = :productId
  AND i.qty_unit = 'BOX'
  AND w.type = 'BOX';
```

**触发时机：** 用户在商品管理页填写/修改 `qtyPerBox` 并保存时，后端自动执行该商品的迁移。

**异常处理：** `qtyPerBox` 为 null 或 0 的商品不迁移，在迁移报告中列出。

### 4. `damage_record` 表 — 新增调拨字段

```sql
ALTER TABLE damage_record
ADD COLUMN cost_deduction        DECIMAL(10,2) COMMENT '破损成本扣除金额 = 破损数 × costPrice',
ADD COLUMN good_qty              INT           COMMENT '破损后好货数量 = qtyPerBox - 破损数',
ADD COLUMN transfer_warehouse_id BIGINT        COMMENT '调拨目标PIECE仓库ID',
ADD COLUMN transfer_price        DECIMAL(10,2) COMMENT '调拨后零售定价（用户填写）';
```

### 5. `inventory_ledger` 表 — 新增历史标记

```sql
ALTER TABLE inventory_ledger
ADD COLUMN qty_unit VARCHAR(10) DEFAULT 'PIECE' 
COMMENT 'PIECE=个(新记录), BOX=箱(迁移前历史记录)';
```

历史记录 `qty_unit` 默认 `'PIECE'`（新增字段），不回填旧数据，流水账页面显示备注说明。

---

## 第二段：业务逻辑

### 入库流程（InOrderService.confirmOrder）

```
确认入库时：
  if warehouse.type == 'BOX':
    if product.qtyPerBox != null && qtyPerBox > 0:
      inventory.qty += item.actualQty × qtyPerBox
      qty_unit = 'PIECE'
      ledger.changeQty = item.actualQty × qtyPerBox
      ledger.qty_unit = 'PIECE'
    else:
      inventory.qty += item.actualQty  （箱数）
      qty_unit = 'BOX'
      ledger.changeQty = item.actualQty
      ledger.qty_unit = 'BOX'
      // 前端显示黄色警告：该商品未设每箱数量
  
  if warehouse.type == 'PIECE':
    inventory.qty += item.actualQty  （个数）
    qty_unit = 'PIECE'
    ledger.qty_unit = 'PIECE'

InOrderItem.price 统一存每个的成本价（若原来存每箱，入库时 ÷ qtyPerBox）
```

### 破损调拨流程（DamageRecordService）

**第一步：登记破损**
```
输入：warehouseId（BOX仓库）、productId、damagedQty（破损个数）

计算：
  cost_deduction = damagedQty × product.costPrice
  good_qty = product.qtyPerBox - damagedQty
  
保存：damage_record（status='PENDING'）
```

**第二步：扣减BOX仓库库存**
```
inventory.qty -= product.qtyPerBox  （扣整箱）

写流水账两条：
  ledger: type='damage',       changeQty = -damagedQty,  qty_unit='PIECE'
  ledger: type='transfer_out', changeQty = -good_qty,    qty_unit='PIECE'
```

**第三步：调拨到PIECE仓库**
```
输入：targetWarehouseId、transferPrice（用户填写零售价）

inventory.qty += good_qty  （PIECE仓库）
qty_unit = 'PIECE'

写流水账一条：
  ledger: type='transfer_in', changeQty = +good_qty, qty_unit='PIECE'

更新 damage_record：
  status = 'RESOLVED'
  transfer_warehouse_id = targetWarehouseId
  transfer_price = transferPrice
  cost_deduction = 已计算值
  good_qty = 已计算值
```

### 财务闭环验证公式

```
期初库存价值
  + 入库金额（actualQty × costPrice）
  - 出库成本（outQty × costPrice）
  - 破损核销（damagedQty × costPrice）
  = 期末库存价值

调拨不影响库存价值总额（只是仓库间转移，成本价不变）
毛利 = 出库金额（售价） - 出库成本（成本价）
```

---

## 第三段：前端交互

### Dashboard.vue — 仓库切换器

```
顶部新增仓库选择器（el-select）：
  选项：[全部仓库] + 各仓库名称
  
  切换时：调用 /reports/dashboard?warehouseId=xxx
  卡片联动：总库存价值、本月入库、本月出库
  表格联动：库存明细列表
```

### 库存列表页 — 箱/个切换

```
新增切换按钮（el-radio-group）：[按箱查看] [按个查看]

displayMode = 'box' | 'piece'

数量列渲染逻辑：
  if displayMode == 'piece':
    显示 qty + '个'
  
  if displayMode == 'box':
    if qty_unit == 'BOX':
      显示 qty + '箱 ⚠️'  （提示未设qtyPerBox）
    if qty_unit == 'PIECE' && warehouse.type == 'BOX':
      箱数 = Math.floor(qty / qtyPerBox)
      散件 = qty % qtyPerBox
      显示 箱数+'箱' + (散件>0 ? ' '+散件+'个' : '')
    if warehouse.type == 'PIECE':
      显示 qty + '个'  （PIECE仓库不显示箱）
```

### 仓库设置页 — 新增类型字段

```
仓库表单新增下拉：
  类型：[按箱仓库(BOX)] [按个仓库(PIECE)]
  
  默认：BOX
  已有仓库：默认显示 BOX，可修改
```

### 破损页 — 调拨流程

```
步骤一：填写破损（现有表单 + cost_deduction 自动计算展示）
步骤二：弹出确认框
  "破损 X 个，成本核销 ¥XX"
  "剩余 Y 个好货，调拨至："
  - 目标仓库下拉（仅显示 PIECE 仓库）
  - 零售定价输入框
  [确认调拨] [暂不调拨]
```

---

## 第四段：报表

### 新增：破损损耗报表

**路由：** `GET /reports/damage?startDate=&endDate=&warehouseId=`  
**前端：** `views/report/Damage.vue`

| 列 | 说明 |
|----|------|
| 日期 | 破损登记时间 |
| 商品 | 商品名称 |
| 仓库 | 来源BOX仓库 |
| 破损数 | 个数 |
| 成本价 | 每个 |
| 损耗金额 | 破损数 × 成本价 |
| 好货调拨 | 个数 |
| 调拨仓库 | 目标PIECE仓库 |
| 零售定价 | transfer_price |

底部合计：总破损数、总损耗金额、总调拨数。

### 现有报表适配

| 报表 | 改动 |
|------|------|
| StockMovement（进退存汇总） | 数量列加箱/个切换（同库存列表逻辑） |
| Inventory（库存价值表） | 数量列加箱/个切换 |
| Ledger（流水账） | 加 qty_unit 列，标注 BOX/PIECE |
| GrossProfit（毛利表） | 不改，金额计算不受单位影响 |
| In / Out（入出库日报） | 加"换算箱数"参考列 |

---

## 实施顺序建议

1. **数据库 DDL**（加字段，不迁移数据）
2. **仓库类型字段**（后端 + 前端仓库设置页）
3. **商品 qtyPerBox 触发迁移**（ProductService 改造）
4. **入库流程适配**（InOrderService + 前端提示）
5. **破损调拨流程**（DamageRecordService + 前端弹窗）
6. **库存列表箱/个切换**（纯前端）
7. **Dashboard 仓库切换器**（前端 + 已有接口）
8. **破损损耗报表**（新接口 + 新页面）
9. **现有报表单位适配**（前端列渲染）

---

## 风险与注意事项

1. **qtyPerBox 为空的商品**：迁移前必须在商品页填写，否则破损调拨流程无法计算 good_qty
2. **历史流水账**：qty_unit 为 'PIECE' 但实际记录的是箱数（迁移前数据），在 Ledger 页面加备注提示
3. **InOrderItem.price**：确认是否原来存的是每箱价还是每个价，迁移时统一换算成每个价
4. **并发安全**：inventory.qty 更新必须用乐观锁或事务（现有系统已有 updateTime 乐观锁）