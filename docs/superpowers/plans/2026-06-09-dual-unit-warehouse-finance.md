# 双单位仓库 + 财务报表改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给仓库加 BOX/PIECE 类型，底层库存统一存「个」，破损后好货自动调拨零售仓，首页可切换仓库，新增破损损耗报表。

**Architecture:** 后端加 `warehouse.type`、`inventory_ledger.qty_unit`、`damage_record` 调拨字段；`stock_snapshot` 是库存读层，迁移时通过插入 `unit_migration` 流水账条目 + upsert snapshot 完成单位转换，不破坏历史数据。前端在 productMap / warehouseMap 中携带完整对象，用 `qtyPerBox` 和 `warehouse.type` 在渲染层做箱/个换算，无需额外接口。

**Tech Stack:** SpringBoot 3, MyBatis-Plus, MySQL 8, Vue 2, Element UI

---

## 文件改动总览

### 后端（`D:/AI/warehouse-backend`）

| 操作 | 文件 |
|------|------|
| 新建 | `docs/migrations/V2__dual_unit.sql` |
| 修改 | `src/main/java/com/warehouse/entity/Warehouse.java` |
| 修改 | `src/main/java/com/warehouse/dto/WarehouseDTO.java` |
| 修改 | `src/main/java/com/warehouse/service/impl/WarehouseServiceImpl.java` |
| 修改 | `src/main/java/com/warehouse/dto/ProductDTO.java` |
| 修改 | `src/main/java/com/warehouse/service/impl/ProductServiceImpl.java` |
| 修改 | `src/main/java/com/warehouse/mapper/StockSnapshotMapper.java` |
| 修改 | `src/main/java/com/warehouse/entity/InventoryLedger.java` |
| 修改 | `src/main/java/com/warehouse/entity/DamageRecord.java` |
| 新建 | `src/main/java/com/warehouse/dto/DamageTransferDTO.java` |
| 修改 | `src/main/java/com/warehouse/service/DamageRecordService.java` |
| 修改 | `src/main/java/com/warehouse/service/impl/DamageRecordServiceImpl.java` |
| 修改 | `src/main/java/com/warehouse/controller/DamageRecordController.java` |
| 修改 | `src/main/java/com/warehouse/service/impl/InOrderServiceImpl.java` |
| 修改 | `src/main/java/com/warehouse/service/ReportService.java` |
| 修改 | `src/main/java/com/warehouse/service/impl/ReportServiceImpl.java` |
| 修改 | `src/main/java/com/warehouse/controller/ReportController.java` |

### 前端（`D:/AI/warehouse-frontend`）

| 操作 | 文件 |
|------|------|
| 修改 | `src/api/warehouse.js` |
| 修改 | `src/api/damageRecord.js` |
| 修改 | `src/api/report.js` |
| 修改 | `src/views/warehouse/List.vue` |
| 修改 | `src/views/Dashboard.vue` |
| 修改 | `src/views/inventory/List.vue` |
| 修改 | `src/views/damage/Index.vue` |
| 新建 | `src/views/report/Damage.vue` |
| 修改 | `src/views/report/StockMovement.vue` |
| 修改 | `src/router/index.js` |

---

## Task 1: 数据库 DDL — 新增字段

**Files:**
- Create: `docs/migrations/V2__dual_unit.sql`

- [ ] **Step 1: 创建迁移 SQL 文件**

```sql
-- V2__dual_unit.sql
-- 迁移前备份
CREATE TABLE IF NOT EXISTS inventory_backup_20260609 AS SELECT * FROM inventory;

-- 仓库类型：BOX=按箱, PIECE=按个
ALTER TABLE warehouse
  ADD COLUMN IF NOT EXISTS type VARCHAR(10) NOT NULL DEFAULT 'BOX'
  COMMENT 'BOX=按箱仓库, PIECE=按个仓库';

-- 破损调拨字段
ALTER TABLE damage_record
  ADD COLUMN IF NOT EXISTS cost_deduction   DECIMAL(10,2) COMMENT '破损成本扣除 = 破损数×costPrice',
  ADD COLUMN IF NOT EXISTS good_qty         INT           COMMENT '破损后好货数量 = qtyPerBox - 破损数',
  ADD COLUMN IF NOT EXISTS transfer_warehouse_id BIGINT   COMMENT '调拨目标PIECE仓库ID',
  ADD COLUMN IF NOT EXISTS transfer_price   DECIMAL(10,2) COMMENT '调拨后零售定价';

-- 流水账历史标记
ALTER TABLE inventory_ledger
  ADD COLUMN IF NOT EXISTS qty_unit VARCHAR(10) DEFAULT 'PIECE'
  COMMENT 'PIECE=个(新记录), BOX=箱(迁移前历史)';
```

- [ ] **Step 2: 在 MySQL 中执行 SQL**

连接生产库（`ssh user@139.84.247.83`，再 `mysql -u warehouse -p`），执行上面的 SQL 文件。预期输出：`Query OK, 0 rows affected` × 4 条。

- [ ] **Step 3: 验证字段已加入**

```sql
DESCRIBE warehouse;
-- 应出现: type | varchar(10) | NO | | BOX |

DESCRIBE damage_record;
-- 应出现: cost_deduction, good_qty, transfer_warehouse_id, transfer_price

DESCRIBE inventory_ledger;
-- 应出现: qty_unit | varchar(10) | YES | | PIECE |
```

- [ ] **Step 4: Commit**

```bash
cd D:/AI/warehouse-backend
git add docs/migrations/V2__dual_unit.sql
git commit -m "db: add warehouse.type, damage_record transfer fields, ledger qty_unit"
```

---

## Task 2: 仓库类型字段（后端 + 前端仓库设置页）

**Files:**
- Modify: `src/main/java/com/warehouse/entity/Warehouse.java`
- Modify: `src/main/java/com/warehouse/dto/WarehouseDTO.java`
- Modify: `src/main/java/com/warehouse/service/impl/WarehouseServiceImpl.java`
- Modify: `src/views/warehouse/List.vue`

- [ ] **Step 1: 在 Warehouse 实体加 type 字段**

打开 `src/main/java/com/warehouse/entity/Warehouse.java`，在 `private String remark;` 后加：

```java
private String type; // BOX / PIECE
```

- [ ] **Step 2: 在 WarehouseDTO 加 type 字段**

打开 `src/main/java/com/warehouse/dto/WarehouseDTO.java`，在 `private String remark;` 后加：

```java
private String type; // BOX / PIECE，默认 BOX
```

- [ ] **Step 3: WarehouseServiceImpl — create/update 传递 type**

打开 `src/main/java/com/warehouse/service/impl/WarehouseServiceImpl.java`。

在 `create()` 方法的 `w.setStatus(...)` 行后加：
```java
w.setType(dto.getType() != null ? dto.getType() : "BOX");
```

在 `update()` 方法的 `if (dto.getStatus() != null)` 块后加：
```java
if (dto.getType() != null) w.setType(dto.getType());
```

- [ ] **Step 4: 前端仓库列表页加类型列和表单字段**

打开 `src/views/warehouse/List.vue`，在 `name` 列后加一列：
```html
<el-table-column prop="type" label="类型" width="100" align="center">
  <template slot-scope="{row}">
    <el-tag :type="row.type === 'PIECE' ? 'success' : 'primary'" size="small">
      {{ row.type === 'PIECE' ? '按个' : '按箱' }}
    </el-tag>
  </template>
</el-table-column>
```

在新建/编辑仓库的 el-form 内加类型选择（在 remark 字段后面）：
```html
<el-form-item label="仓库类型" prop="type">
  <el-select v-model="form.type" style="width:100%;">
    <el-option label="按箱仓库 (BOX)" value="BOX" />
    <el-option label="按个仓库 (PIECE)" value="PIECE" />
  </el-select>
</el-form-item>
```

在 `data()` 的 `form` 对象中加 `type: 'BOX'`。

- [ ] **Step 5: 启动后端，在浏览器打开仓库管理页，确认新建/编辑弹窗有类型下拉，现有仓库显示「按箱」tag**

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/warehouse/entity/Warehouse.java \
        src/main/java/com/warehouse/dto/WarehouseDTO.java \
        src/main/java/com/warehouse/service/impl/WarehouseServiceImpl.java
git commit -m "feat: add warehouse.type (BOX/PIECE)"

cd D:/AI/warehouse-frontend
git add src/views/warehouse/List.vue
git commit -m "feat: warehouse list/form add type field"
```

---

## Task 3: 商品 qtyPerBox 保存时触发库存单位迁移

**Files:**
- Modify: `src/main/java/com/warehouse/dto/ProductDTO.java`
- Modify: `src/main/java/com/warehouse/service/impl/ProductServiceImpl.java`
- Modify: `src/main/java/com/warehouse/mapper/StockSnapshotMapper.java`
- Modify: `src/main/java/com/warehouse/entity/InventoryLedger.java`

- [ ] **Step 1: ProductDTO 加 qtyPerBox 和 costPrice 字段**

打开 `src/main/java/com/warehouse/dto/ProductDTO.java`，在最后加：

```java
private Integer qtyPerBox;  // 每箱个数
private BigDecimal costPrice; // 成本价（允许前端传入覆盖）
```

- [ ] **Step 2: InventoryLedger 实体加 qtyUnit 字段**

打开 `src/main/java/com/warehouse/entity/InventoryLedger.java`，在 `private LocalDateTime createdAt;` 后加：

```java
private String qtyUnit; // PIECE / BOX
```

- [ ] **Step 3: StockSnapshotMapper 加迁移查询方法**

打开 `src/main/java/com/warehouse/mapper/StockSnapshotMapper.java`，在末尾加：

```java
/** 查某商品在所有 BOX 仓库的快照（迁移用）*/
@Select("SELECT ss.* FROM stock_snapshot ss " +
        "JOIN warehouse w ON w.id = ss.location_id " +
        "WHERE ss.product_id = #{productId} AND w.type = 'BOX' FOR UPDATE")
List<StockSnapshot> selectBoxWarehouseSnapshotsForUpdate(@Param("productId") Long productId);
```

- [ ] **Step 4: ProductServiceImpl — update() 触发迁移**

打开 `src/main/java/com/warehouse/service/impl/ProductServiceImpl.java`。

在类顶部注入新依赖：
```java
private final StockSnapshotMapper snapshotMapper;
private final InventoryLedgerMapper ledgerMapper;
```

（需要在构造函数参数里同步加上这两个 final 字段，`@RequiredArgsConstructor` 会自动生成构造器。）

替换整个 `update()` 方法为：

```java
@Override
@Transactional
public void update(ProductDTO dto) {
    Product p = productMapper.selectById(dto.getId());
    if (p == null) throw new BusinessException("商品不存在");

    boolean qtyPerBoxChanged = dto.getQtyPerBox() != null
            && dto.getQtyPerBox() > 0
            && !dto.getQtyPerBox().equals(p.getQtyPerBox());

    p.setName(dto.getName()); p.setCategoryId(dto.getCategoryId());
    p.setUnit(dto.getUnit()); p.setPrice(dto.getPrice());
    p.setImage(dto.getImage()); p.setRemark(dto.getRemark());
    if (dto.getStatus() != null) p.setStatus(dto.getStatus());
    if (dto.getQtyPerBox() != null && dto.getQtyPerBox() > 0)
        p.setQtyPerBox(dto.getQtyPerBox());
    productMapper.updateById(p);

    // 若 qtyPerBox 新填或变更，自动迁移该商品在所有 BOX 仓库的库存到「个」单位
    if (qtyPerBoxChanged) {
        migrateBoxInventoryToPiece(p.getId(), dto.getQtyPerBox());
    }
}

private void migrateBoxInventoryToPiece(Long productId, int qtyPerBox) {
    List<StockSnapshot> snaps = snapshotMapper.selectBoxWarehouseSnapshotsForUpdate(productId);
    for (StockSnapshot snap : snaps) {
        BigDecimal oldQty = snap.getCurrentQty() != null ? snap.getCurrentQty() : BigDecimal.ZERO;
        // 补差：新增 (qtyPerBox - 1) 倍的现有数量
        BigDecimal delta = oldQty.multiply(BigDecimal.valueOf(qtyPerBox - 1));
        if (delta.compareTo(BigDecimal.ZERO) == 0) continue;

        InventoryLedger entry = new InventoryLedger();
        entry.setId(java.util.UUID.randomUUID().toString());
        entry.setProductId(productId);
        entry.setLocationId(snap.getLocationId());
        entry.setChangeQty(delta);
        entry.setType("unit_migration");
        entry.setQtyUnit("PIECE");
        entry.setNote("单位迁移：1箱=" + qtyPerBox + "个，原qty=" + oldQty.intValue() + "箱");
        entry.setOccurredAt(java.time.LocalDateTime.now());
        entry.setSynced(1);
        entry.setCreatedAt(java.time.LocalDateTime.now());
        ledgerMapper.insert(entry);

        BigDecimal newQty = oldQty.multiply(BigDecimal.valueOf(qtyPerBox));
        snapshotMapper.upsert(productId, snap.getLocationId(), newQty,
                snap.getAlertQty() != null ? snap.getAlertQty() : 0);
    }
}
```

需要在文件顶部加 import：
```java
import java.math.BigDecimal;
import java.util.List;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.InventoryLedgerMapper;
```

- [ ] **Step 5: 前端商品表单加 qtyPerBox 字段**

打开 `src/views/product/List.vue`，在新建/编辑商品表单（`spec` 字段附近）加：

```html
<el-form-item label="每箱个数">
  <el-input-number v-model="form.qtyPerBox" :min="1" :precision="0"
    placeholder="留空表示未定" style="width:100%;" />
</el-form-item>
```

在 `data()` 的 `form` 对象中加 `qtyPerBox: null`。确保 `updateProduct(id, form)` 调用会携带 `qtyPerBox`。

- [ ] **Step 6: 测试迁移**

1. 在商品管理页，找一个已有库存的商品（比如当前qty=10，代表10箱）
2. 填写「每箱个数」= 24，点保存
3. 去库存列表，确认该商品库存变为 240
4. 去流水账（inventory/ledger），确认有一条 `type=unit_migration` 的记录，changeQty=216

- [ ] **Step 7: Commit**

```bash
cd D:/AI/warehouse-backend
git add src/main/java/com/warehouse/entity/InventoryLedger.java \
        src/main/java/com/warehouse/dto/ProductDTO.java \
        src/main/java/com/warehouse/service/impl/ProductServiceImpl.java \
        src/main/java/com/warehouse/mapper/StockSnapshotMapper.java
git commit -m "feat: qtyPerBox save triggers BOX→PIECE inventory migration"

cd D:/AI/warehouse-frontend
git add src/views/product/List.vue
git commit -m "feat: product form add qtyPerBox field"
```

---

## Task 4: 入库流程适配（单位换算 + 前端警告）

**Files:**
- Modify: `src/main/java/com/warehouse/service/impl/InOrderServiceImpl.java`
- Modify: `src/main/java/com/warehouse/mapper/WarehouseMapper.java`
- Modify: `src/views/inOrder/Create.vue`

- [ ] **Step 1: WarehouseMapper 加按 ID 查 type 的方法**

打开 `src/main/java/com/warehouse/mapper/WarehouseMapper.java`，加：

```java
@Select("SELECT type FROM warehouse WHERE id = #{id}")
String selectTypeById(@Param("id") Long id);
```

- [ ] **Step 2: InOrderServiceImpl.confirm() — 入库时按仓库类型换算 qty**

打开 `src/main/java/com/warehouse/service/impl/InOrderServiceImpl.java`，在依赖注入字段里加：
```java
private final WarehouseMapper warehouseMapper;
```

找到 `confirm()` 方法中循环体内的：
```java
int qty = item.getActualQty() != null ? item.getActualQty()
        : (item.getPlanQty() != null ? item.getPlanQty() : 0);
if (qty <= 0) continue;
```

替换为：
```java
int rawQty = item.getActualQty() != null ? item.getActualQty()
        : (item.getPlanQty() != null ? item.getPlanQty() : 0);
if (rawQty <= 0) continue;

// 若 BOX 仓库且商品已设 qtyPerBox，入库数量换算为个数
String warehouseType = warehouseMapper.selectTypeById(order.getWarehouseId());
Product productForUnit = productMapper.selectById(item.getProductId());
int qtyPerBox = (productForUnit != null && productForUnit.getQtyPerBox() != null
        && productForUnit.getQtyPerBox() > 0) ? productForUnit.getQtyPerBox() : 1;
boolean isBoxWarehouse = "BOX".equals(warehouseType);
int qty = isBoxWarehouse ? rawQty * qtyPerBox : rawQty;
// item.price 统一视作每个成本价（BOX仓库：用户填的是每箱价，÷qtyPerBox换算）
if (isBoxWarehouse && item.getPrice() != null
        && item.getPrice().compareTo(BigDecimal.ZERO) > 0 && qtyPerBox > 1) {
    item.setPrice(item.getPrice().divide(
            BigDecimal.valueOf(qtyPerBox), 4, java.math.RoundingMode.HALF_UP));
    inOrderItemMapper.updateById(item);
}
```

同时在创建 `entry` 时加上 qty_unit：
```java
entry.setQtyUnit("PIECE");
```

- [ ] **Step 3: 前端入库创建页 — 商品无 qtyPerBox 时显示警告**

打开 `src/views/inOrder/Create.vue`，找到商品选择后显示的明细行。在每行商品名称旁加：

```html
<el-tag v-if="isBoxWarehouse && !item.qtyPerBox" type="warning" size="mini" style="margin-left:6px;">
  ⚠️ 未设每箱数量
</el-tag>
```

在 `computed` 或 `methods` 中加：
```js
isBoxWarehouse() {
  const w = this.warehouses.find(w => w.id === this.form.warehouseId)
  return w && w.type === 'BOX'
}
```

确保 `warehouses` 列表包含完整对象（含 `type` 字段，`GET /warehouses` 已返回）。

商品明细的 `item.qtyPerBox` 从商品列表接口获取（`GET /products` 返回完整 Product 包含 `qtyPerBox`）。

- [ ] **Step 4: Commit**

```bash
cd D:/AI/warehouse-backend
git add src/main/java/com/warehouse/mapper/WarehouseMapper.java \
        src/main/java/com/warehouse/service/impl/InOrderServiceImpl.java
git commit -m "feat: inbound confirm converts box qty to piece qty"

cd D:/AI/warehouse-frontend
git add src/views/inOrder/Create.vue
git commit -m "feat: inbound form warn when product qtyPerBox is missing"
```

---

## Task 5: 破损调拨流程（后端 + 前端弹窗）

**Files:**
- Modify: `src/main/java/com/warehouse/entity/DamageRecord.java`
- Create: `src/main/java/com/warehouse/dto/DamageTransferDTO.java`
- Modify: `src/main/java/com/warehouse/service/DamageRecordService.java`
- Modify: `src/main/java/com/warehouse/service/impl/DamageRecordServiceImpl.java`
- Modify: `src/main/java/com/warehouse/controller/DamageRecordController.java`
- Modify: `src/api/damageRecord.js`
- Modify: `src/views/damage/Index.vue`

- [ ] **Step 1: DamageRecord 实体加调拨字段**

打开 `src/main/java/com/warehouse/entity/DamageRecord.java`，在 `private String source;` 后加：

```java
private java.math.BigDecimal costDeduction;
private Integer goodQty;
private Long transferWarehouseId;
private java.math.BigDecimal transferPrice;
```

- [ ] **Step 2: 新建 DamageTransferDTO**

创建 `src/main/java/com/warehouse/dto/DamageTransferDTO.java`：

```java
package com.warehouse.dto;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class DamageTransferDTO {
    @NotNull(message = "目标仓库不能为空")
    private Long targetWarehouseId;

    @NotNull(message = "零售价不能为空")
    @Min(value = 0, message = "零售价不能为负")
    private BigDecimal retailPrice;
}
```

- [ ] **Step 3: DamageRecordService 接口加 transfer 方法**

打开 `src/main/java/com/warehouse/service/DamageRecordService.java`，加：

```java
void transfer(Long damageId, DamageTransferDTO dto, String operator);
```

并在文件顶加 import：
```java
import com.warehouse.dto.DamageTransferDTO;
```

- [ ] **Step 4: DamageRecordServiceImpl 实现 transfer()**

打开 `src/main/java/com/warehouse/service/impl/DamageRecordServiceImpl.java`。

在类顶加注入字段：
```java
private final StockSnapshotMapper snapshotMapper;
private final InventoryLedgerMapper ledgerMapper;
private final ProductMapper productMapper;
private final WarehouseMapper warehouseMapper;
```

在末尾实现 `transfer()`：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void transfer(Long damageId, DamageTransferDTO dto, String operator) {
    DamageRecord record = damageRecordMapper.selectById(damageId);
    if (record == null) throw new BusinessException("损坏记录不存在");
    if ("RESOLVED".equals(record.getStatus())) throw new BusinessException("已核销，不可重复调拨");

    Product product = productMapper.selectById(record.getProductId());
    if (product == null) throw new BusinessException("商品不存在");
    if (product.getQtyPerBox() == null || product.getQtyPerBox() <= 0)
        throw new BusinessException("该商品未设每箱数量，无法计算好货数量");

    int damagedQty = record.getQty();
    int qtyPerBox  = product.getQtyPerBox();
    int goodQty    = qtyPerBox - damagedQty;
    if (goodQty < 0) throw new BusinessException("破损数量(" + damagedQty + ")超过每箱数量(" + qtyPerBox + ")");

    java.math.BigDecimal costPrice = product.getCostPrice() != null ? product.getCostPrice() : java.math.BigDecimal.ZERO;
    java.math.BigDecimal costDeduction = costPrice.multiply(java.math.BigDecimal.valueOf(damagedQty));

    String docNo = "DMG" + damageId;

    // 1. 从 BOX 仓库扣整箱（扣 qtyPerBox 个）
    StockSnapshot boxSnap = snapshotMapper.selectOneForUpdate(record.getProductId(), record.getWarehouseId());
    java.math.BigDecimal boxBefore = boxSnap != null ? boxSnap.getCurrentQty() : java.math.BigDecimal.ZERO;
    java.math.BigDecimal boxAfter  = boxBefore.subtract(java.math.BigDecimal.valueOf(qtyPerBox));
    if (boxAfter.compareTo(java.math.BigDecimal.ZERO) < 0)
        throw new BusinessException("BOX仓库库存不足，当前库存" + boxBefore.intValue() + "个");

    insertLedger(record.getProductId(), record.getWarehouseId(),
            java.math.BigDecimal.valueOf(-damagedQty), "damage", docNo, "破损核销", operator);
    if (goodQty > 0) {
        insertLedger(record.getProductId(), record.getWarehouseId(),
                java.math.BigDecimal.valueOf(-goodQty), "transfer_out", docNo, "调拨出BOX仓", operator);
    }
    snapshotMapper.upsert(record.getProductId(), record.getWarehouseId(), boxAfter,
            boxSnap != null && boxSnap.getAlertQty() != null ? boxSnap.getAlertQty() : 0);

    // 2. 调拨好货到 PIECE 仓库
    if (goodQty > 0) {
        StockSnapshot pieceSnap = snapshotMapper.selectOneForUpdate(record.getProductId(), dto.getTargetWarehouseId());
        java.math.BigDecimal pieceBefore = pieceSnap != null ? pieceSnap.getCurrentQty() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal pieceAfter  = pieceBefore.add(java.math.BigDecimal.valueOf(goodQty));
        insertLedger(record.getProductId(), dto.getTargetWarehouseId(),
                java.math.BigDecimal.valueOf(goodQty), "transfer_in", docNo, "调拨入PIECE仓，零售价" + dto.getRetailPrice(), operator);
        snapshotMapper.upsert(record.getProductId(), dto.getTargetWarehouseId(), pieceAfter,
                pieceSnap != null && pieceSnap.getAlertQty() != null ? pieceSnap.getAlertQty() : 0);
    }

    // 3. 更新 damage_record
    record.setStatus("RESOLVED");
    record.setCostDeduction(costDeduction);
    record.setGoodQty(goodQty);
    record.setTransferWarehouseId(dto.getTargetWarehouseId());
    record.setTransferPrice(dto.getRetailPrice());
    record.setResolvedAt(java.time.LocalDateTime.now());
    damageRecordMapper.updateById(record);
}

private void insertLedger(Long productId, Long locationId, java.math.BigDecimal qty,
                           String type, String docNo, String note, String operator) {
    InventoryLedger e = new InventoryLedger();
    e.setId(java.util.UUID.randomUUID().toString());
    e.setProductId(productId);
    e.setLocationId(locationId);
    e.setChangeQty(qty);
    e.setType(type);
    e.setDocumentNo(docNo);
    e.setOperator(operator != null ? operator : "");
    e.setNote(note);
    e.setQtyUnit("PIECE");
    e.setOccurredAt(java.time.LocalDateTime.now());
    e.setSynced(1);
    e.setCreatedAt(java.time.LocalDateTime.now());
    ledgerMapper.insert(e);
}
```

需要在顶部加 imports：
```java
import com.warehouse.dto.DamageTransferDTO;
import com.warehouse.entity.*;
import com.warehouse.mapper.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
```

- [ ] **Step 5: DamageRecordController 加调拨接口**

打开 `src/main/java/com/warehouse/controller/DamageRecordController.java`，在 `delete` 方法后加：

```java
@PostMapping("/{id}/transfer")
@PreAuthorize("isAuthenticated()")
public Result<Void> transfer(@PathVariable Long id,
                             @RequestBody @Validated DamageTransferDTO dto,
                             @AuthenticationPrincipal UserDetails user) {
    String username = ((JwtUserDetails) user).getUsername();
    damageRecordService.transfer(id, dto, username);
    return Result.success();
}
```

在文件顶加 import：
```java
import com.warehouse.dto.DamageTransferDTO;
```

- [ ] **Step 6: 前端 API 加 transferDamage**

打开 `src/api/damageRecord.js`，加：
```js
export const transferDamage = (id, data) => request.post(`/damage-records/${id}/transfer`, data)
```

- [ ] **Step 7: 前端破损页加调拨弹窗**

打开 `src/views/damage/Index.vue`。

在表格操作列中（`<el-button type="danger" ...>删除</el-button>` 旁边）加：
```html
<el-button v-if="row.status === 'PENDING'" size="mini" type="primary"
  style="margin-left:4px;" @click="openTransfer(row)">调拨</el-button>
```

在表格末尾（`</el-dialog>` 后）加调拨弹窗：
```html
<el-dialog title="破损调拨到零售仓" :visible.sync="transferDialog" width="480px" @close="resetTransfer">
  <div v-if="transferRecord" style="margin-bottom:16px;padding:12px;background:#fdf6ec;border-radius:6px;">
    <div>商品：<b>{{ transferRecord.productName }}</b></div>
    <div>破损数：<b>{{ transferRecord.qty }} 个</b></div>
    <div>成本核销预估：<b>¥ {{ transferCostPreview }}</b></div>
    <div v-if="transferGoodQtyPreview >= 0">好货可调拨：<b>{{ transferGoodQtyPreview }} 个</b></div>
  </div>
  <el-form :model="transferForm" :rules="transferRules" ref="transferForm" label-width="100px">
    <el-form-item label="目标仓库" prop="targetWarehouseId">
      <el-select v-model="transferForm.targetWarehouseId" placeholder="请选择PIECE仓库" style="width:100%;">
        <el-option v-for="w in pieceWarehouses" :key="w.id" :label="w.name" :value="w.id" />
      </el-select>
    </el-form-item>
    <el-form-item label="零售定价" prop="retailPrice">
      <el-input-number v-model="transferForm.retailPrice" :min="0" :precision="2" style="width:100%;"
        placeholder="每个零售价" />
    </el-form-item>
  </el-form>
  <div slot="footer">
    <el-button @click="transferDialog = false">取消</el-button>
    <el-button type="primary" :loading="transferring" @click="handleTransfer">确认调拨</el-button>
  </div>
</el-dialog>
```

在 `import` 行加：
```js
import { getDamageRecords, createDamageRecord, deleteDamageRecord, transferDamage } from '../../api/damageRecord'
```

在 `data()` 加：
```js
transferDialog: false,
transferRecord: null,
transferForm: { targetWarehouseId: null, retailPrice: null },
transferRules: {
  targetWarehouseId: [{ required: true, message: '请选择目标仓库', trigger: 'change' }],
  retailPrice: [{ required: true, message: '请填写零售价', trigger: 'blur' }]
},
transferring: false,
```

在 `computed` 加：
```js
pieceWarehouses() {
  return this.warehouses.filter(w => w.type === 'PIECE')
},
transferCostPreview() {
  if (!this.transferRecord) return '0.00'
  // 这里只展示预估，实际由后端计算
  return '—'
},
transferGoodQtyPreview() {
  if (!this.transferRecord) return -1
  return -1 // 后端有qtyPerBox才能算，前端显示「—」
},
```

在 `methods` 加：
```js
openTransfer(row) {
  this.transferRecord = row
  this.transferForm = { targetWarehouseId: null, retailPrice: null }
  this.transferDialog = true
},
resetTransfer() {
  this.transferRecord = null
},
async handleTransfer() {
  this.$refs.transferForm.validate(async valid => {
    if (!valid) return
    this.transferring = true
    try {
      await transferDamage(this.transferRecord.id, this.transferForm)
      this.$message.success('调拨成功')
      this.transferDialog = false
      this.loadData()
    } catch(e) {
      this.$message.error(e.response?.data?.message || '调拨失败')
    } finally {
      this.transferring = false
    }
  })
},
```

- [ ] **Step 8: 验证调拨流程**

1. 在破损记录页新建一条 PENDING 状态的破损记录（商品需已设 qtyPerBox）
2. 点击「调拨」按钮，确认弹窗显示正常
3. 选择一个 PIECE 仓库，填写零售价，提交
4. 确认破损记录变为 RESOLVED
5. 去库存列表，确认 BOX 仓库扣减了整箱，PIECE 仓库增加了好货数量
6. 去流水账，确认有 damage、transfer_out、transfer_in 三条记录

- [ ] **Step 9: Commit**

```bash
cd D:/AI/warehouse-backend
git add src/main/java/com/warehouse/entity/DamageRecord.java \
        src/main/java/com/warehouse/dto/DamageTransferDTO.java \
        src/main/java/com/warehouse/service/DamageRecordService.java \
        src/main/java/com/warehouse/service/impl/DamageRecordServiceImpl.java \
        src/main/java/com/warehouse/controller/DamageRecordController.java
git commit -m "feat: damage transfer flow — deduct BOX, write off damage cost, credit PIECE warehouse"

cd D:/AI/warehouse-frontend
git add src/api/damageRecord.js src/views/damage/Index.vue
git commit -m "feat: damage page add transfer dialog"
```

---

## Task 6: 库存列表箱/个切换（纯前端）

**Files:**
- Modify: `src/views/inventory/List.vue`

- [ ] **Step 1: 在仓库和商品数据加载时保存完整对象**

打开 `src/views/inventory/List.vue`。

找到：
```js
this.warehouseMap = Object.fromEntries(this.warehouses.map(w => [w.id, w.name]))
```
替换为：
```js
this.warehouseMap = Object.fromEntries(this.warehouses.map(w => [w.id, w]))
```

找到（在 `loadAllProducts` 方法中）：
```js
this.productMap = Object.fromEntries(all.map(p => [p.id, p.name]))
```
替换为：
```js
this.productMap = Object.fromEntries(all.map(p => [p.id, p]))
```

找到所有使用 `warehouseMap[row.warehouseId]` 的地方，替换为 `warehouseMap[row.warehouseId]?.name || row.warehouseId`。

找到所有使用 `productMap[row.productId]` 的地方，替换为 `productMap[row.productId]?.name || row.productId`。

- [ ] **Step 2: 加 displayMode 状态和格式化方法**

在 `data()` 加：
```js
displayMode: 'box', // 'box' | 'piece'
```

在 `methods` 加：
```js
formatQty(row) {
  const wh = this.warehouseMap[row.warehouseId]
  const prod = this.productMap[row.productId]
  const qty = row.qty
  const qtyPerBox = prod?.qtyPerBox

  if (this.displayMode === 'piece') {
    return qty + ' 个'
  }
  // 按箱显示
  if (wh?.type === 'PIECE') {
    return qty + ' 个'
  }
  if (!qtyPerBox) {
    return qty + ' 箱 ⚠️'
  }
  const boxes = Math.floor(qty / qtyPerBox)
  const loose = qty % qtyPerBox
  return loose > 0 ? `${boxes}箱 ${loose}个` : `${boxes}箱`
},
```

- [ ] **Step 3: 在列表顶部加切换按钮，修改库存数量列**

在 `el-card` 内顶部工具栏（`<div style="margin-bottom:16px...">`）的末尾加：

```html
<el-radio-group v-model="displayMode" size="small" style="margin-left:8px;">
  <el-radio-button label="box">按箱</el-radio-button>
  <el-radio-button label="piece">按个</el-radio-button>
</el-radio-group>
```

找到库存数量列：
```html
<el-table-column prop="qty" label="当前库存" width="110" align="center" />
```
替换为：
```html
<el-table-column label="当前库存" width="130" align="center">
  <template slot-scope="{row}">{{ formatQty(row) }}</template>
</el-table-column>
```

同样修改移动端 `m-inv-num-val` 处的显示：把 `{{ row.qty }}` 替换为 `{{ formatQty(row) }}`。

- [ ] **Step 4: 验证**

1. 打开库存列表，确认默认显示「按箱」
2. 对于已设 qtyPerBox 的商品，显示「X箱」或「X箱Y个」
3. 对于未设 qtyPerBox 的商品，显示「X箱 ⚠️」
4. PIECE 仓库的商品始终显示「X个」
5. 切换为「按个」后，所有商品显示个数

- [ ] **Step 5: Commit**

```bash
cd D:/AI/warehouse-frontend
git add src/views/inventory/List.vue
git commit -m "feat: inventory list add box/piece display toggle"
```

---

## Task 7: Dashboard 仓库切换器

**Files:**
- Modify: `src/views/Dashboard.vue`

- [ ] **Step 1: 加仓库选择器数据**

打开 `src/views/Dashboard.vue`，在 `data()` 中加：
```js
selectedWarehouseId: null, // null = 全部仓库
warehouseList: [],
```

在 `created()` 或 `mounted()` 中加（在现有加载数据逻辑旁）：
```js
getWarehouses().then(r => { this.warehouseList = r.data || [] })
```

在 `import` 中加：
```js
import { getWarehouses } from '../api/warehouse'
```

- [ ] **Step 2: 在顶部加仓库选择器**

在桌面端 `<el-row :gutter="20" style="margin-bottom:20px;">` 前加：

```html
<div style="margin-bottom:16px;display:flex;align-items:center;gap:12px;">
  <span style="color:#606266;font-weight:500;">查看仓库：</span>
  <el-select v-model="selectedWarehouseId" placeholder="全部仓库" clearable
    style="width:200px;" @change="onWarehouseSwitch">
    <el-option label="全部仓库" :value="null" />
    <el-option v-for="w in warehouseList" :key="w.id" :label="w.name" :value="w.id" />
  </el-select>
</div>
```

- [ ] **Step 3: 仓库切换时刷新数据**

找到现有的 `loadStats()` 或 `loadDashboard()` 方法（负责调用 `/reports/dashboard`），修改为接受 `warehouseId` 参数：

```js
async loadStats() {
  // 对 /reports/dashboard 追加 warehouseId 参数
  // 注意：后端 getDashboardStats() 暂时不支持 warehouseId，先用 inventorySummary 补充
  const params = this.selectedWarehouseId ? { warehouseId: this.selectedWarehouseId } : {}
  const r = await getDashboardStats()  // 保持原调用，全局统计不变
  // 另调 inventory summary 做仓库级过滤
  const invR = await getInventorySummary(params)
  this.statsData = { ...this.statsData, ...invR.data }
},
onWarehouseSwitch() {
  this.loadStats()
  this.loadChartData()  // 图表也跟随切换（已有 warehouseId 支持）
},
```

在 `loadChartData()` 中，把仓库图表的 warehouseId 改为使用 `this.selectedWarehouseId`：
```js
async loadChartData() {
  const warehouseId = this.selectedWarehouseId || this.chartWarehouseId
  // ... 原有逻辑
}
```

- [ ] **Step 4: 验证**

1. 打开首页，仓库选择器默认显示「全部仓库」，数据和之前一致
2. 选择某个具体仓库，库存卡片和图表更新为该仓库数据
3. 清空选择，恢复全部

- [ ] **Step 5: Commit**

```bash
cd D:/AI/warehouse-frontend
git add src/views/Dashboard.vue
git commit -m "feat: dashboard add warehouse selector"
```

---

## Task 8: 新增破损损耗报表

**Files:**
- Modify: `src/main/java/com/warehouse/service/ReportService.java`
- Modify: `src/main/java/com/warehouse/service/impl/ReportServiceImpl.java`
- Modify: `src/main/java/com/warehouse/controller/ReportController.java`
- Modify: `src/api/report.js`
- Create: `src/views/report/Damage.vue`
- Modify: `src/router/index.js`

- [ ] **Step 1: ReportService 接口加方法**

打开 `src/main/java/com/warehouse/service/ReportService.java`，加：

```java
List<Map<String, Object>> damageReport(LocalDate startDate, LocalDate endDate, Long warehouseId);
```

- [ ] **Step 2: ReportServiceImpl 实现 damageReport()**

打开 `src/main/java/com/warehouse/service/impl/ReportServiceImpl.java`，实现：

```java
@Override
public List<Map<String, Object>> damageReport(LocalDate startDate, LocalDate endDate, Long warehouseId) {
    String sql = "SELECT " +
        "  DATE(d.created_at) AS date, " +
        "  p.name AS productName, " +
        "  p.sku_code AS skuCode, " +
        "  w.name AS warehouseName, " +
        "  d.qty AS damagedQty, " +
        "  p.cost_price AS costPrice, " +
        "  d.cost_deduction AS costDeduction, " +
        "  d.good_qty AS goodQty, " +
        "  tw.name AS transferWarehouseName, " +
        "  d.transfer_price AS transferPrice, " +
        "  d.status, " +
        "  d.remark " +
        "FROM damage_record d " +
        "JOIN product p ON p.id = d.product_id " +
        "JOIN warehouse w ON w.id = d.warehouse_id " +
        "LEFT JOIN warehouse tw ON tw.id = d.transfer_warehouse_id " +
        "WHERE d.created_at >= :startDate AND d.created_at < :endDate " +
        (warehouseId != null ? "AND d.warehouse_id = :warehouseId " : "") +
        "ORDER BY d.created_at DESC";
    // 使用 JdbcTemplate 或 MyBatis，以现有 ReportServiceImpl 的风格为准
    // （若使用 JdbcTemplate：）
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("startDate", startDate.atStartOfDay())
        .addValue("endDate", endDate.plusDays(1).atStartOfDay());
    if (warehouseId != null) params.addValue("warehouseId", warehouseId);
    return namedJdbc.queryForList(sql, params);
}
```

> 注意：需查看 `ReportServiceImpl` 现有风格（JdbcTemplate / MyBatis），用同样的方式写查询。如果用 `@Mapper` 风格，则在 `ReportMapper` 里加对应的 XML 查询。

- [ ] **Step 3: ReportController 加接口**

打开 `src/main/java/com/warehouse/controller/ReportController.java`，加：

```java
@GetMapping("/damage")
public Result<List<Map<String, Object>>> damageReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) Long warehouseId) {
    return Result.success(reportService.damageReport(startDate, endDate, warehouseId));
}
```

- [ ] **Step 4: 前端 API 加方法**

打开 `src/api/report.js`，加：
```js
export const getDamageReport = params => request.get('/reports/damage', { params })
```

- [ ] **Step 5: 新建破损损耗报表页面**

创建 `src/views/report/Damage.vue`：

```vue
<template>
  <el-card>
    <div style="display:flex;gap:12px;margin-bottom:16px;flex-wrap:wrap;align-items:center;">
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
        start-placeholder="开始日期" end-placeholder="结束日期" style="width:240px;" />
      <el-select v-model="query.warehouseId" placeholder="全部仓库" clearable style="width:160px;">
        <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
      </el-select>
      <el-button type="primary" icon="el-icon-search" @click="loadData">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe show-summary
      :summary-method="getSummary">
      <el-table-column prop="date" label="日期" width="110" />
      <el-table-column prop="productName" label="商品" min-width="140" show-overflow-tooltip />
      <el-table-column prop="warehouseName" label="来源仓库" min-width="120" />
      <el-table-column prop="damagedQty" label="破损数(个)" width="100" align="center" />
      <el-table-column label="成本价" width="100" align="right">
        <template slot-scope="{row}">{{ row.costPrice ? '¥' + Number(row.costPrice).toFixed(2) : '—' }}</template>
      </el-table-column>
      <el-table-column label="损耗金额" width="110" align="right">
        <template slot-scope="{row}">
          <span style="color:#F56C6C;">{{ row.costDeduction ? '¥' + Number(row.costDeduction).toFixed(2) : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="goodQty" label="好货调拨(个)" width="110" align="center">
        <template slot-scope="{row}">{{ row.goodQty != null ? row.goodQty : '—' }}</template>
      </el-table-column>
      <el-table-column prop="transferWarehouseName" label="调拨仓库" min-width="120">
        <template slot-scope="{row}">{{ row.transferWarehouseName || '—' }}</template>
      </el-table-column>
      <el-table-column label="零售定价" width="100" align="right">
        <template slot-scope="{row}">{{ row.transferPrice ? '¥' + Number(row.transferPrice).toFixed(2) : '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template slot-scope="{row}">
          <el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'" size="small">
            {{ row.status === 'RESOLVED' ? '已核销' : '待处理' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script>
import { getDamageReport } from '../../api/report'
import { getWarehouses } from '../../api/warehouse'
export default {
  name: 'DamageReport',
  data() {
    const end = new Date()
    const start = new Date(); start.setDate(1)
    return {
      list: [], loading: false, warehouses: [],
      dateRange: [start, end],
      query: { warehouseId: null }
    }
  },
  created() {
    getWarehouses().then(r => { this.warehouses = r.data || [] })
    this.loadData()
  },
  methods: {
    async loadData() {
      if (!this.dateRange) return
      this.loading = true
      const [s, e] = this.dateRange
      const fmt = d => d.toISOString().slice(0, 10)
      const r = await getDamageReport({ startDate: fmt(s), endDate: fmt(e), ...this.query })
              .finally(() => { this.loading = false })
      this.list = r.data || []
    },
    getSummary({ columns, data }) {
      return columns.map((col, i) => {
        if (i === 0) return '合计'
        if (col.property === 'damagedQty') return data.reduce((s, r) => s + (r.damagedQty || 0), 0) + ' 个'
        if (col.property === 'goodQty') return data.reduce((s, r) => s + (r.goodQty || 0), 0) + ' 个'
        if (col.label === '损耗金额') {
          const total = data.reduce((s, r) => s + Number(r.costDeduction || 0), 0)
          return '¥' + total.toFixed(2)
        }
        return ''
      })
    }
  }
}
</script>
```

- [ ] **Step 6: 路由加破损报表**

打开 `src/router/index.js`，在报表路由列表末尾加：
```js
{ path: 'report/damage', component: () => import('../views/report/Damage.vue') },
```

导航菜单（找到 `src/components/Layout/index.vue` 或 `Sidebar.vue`）中加报表菜单项：
```html
<el-menu-item index="/report/damage">破损损耗表</el-menu-item>
```

- [ ] **Step 7: 验证**

1. 打开 `/report/damage`，确认页面正常加载
2. 查询一个有破损记录的时间范围，确认数据显示正确
3. 确认合计行显示正确的破损数和损耗金额

- [ ] **Step 8: Commit**

```bash
cd D:/AI/warehouse-backend
git add src/main/java/com/warehouse/service/ReportService.java \
        src/main/java/com/warehouse/service/impl/ReportServiceImpl.java \
        src/main/java/com/warehouse/controller/ReportController.java
git commit -m "feat: add damage report endpoint GET /reports/damage"

cd D:/AI/warehouse-frontend
git add src/api/report.js src/views/report/Damage.vue src/router/index.js
git commit -m "feat: add damage loss report page"
```

---

## Task 9: 现有报表单位适配

**Files:**
- Modify: `src/views/report/StockMovement.vue`
- Modify: `src/views/report/Inventory.vue`

- [ ] **Step 1: StockMovement 报表加箱/个切换**

打开 `src/views/report/StockMovement.vue`，找到数量类的列（`inQty`、`outQty`、`endQty` 等）。

在顶部筛选栏加切换：
```html
<el-radio-group v-model="displayMode" size="small">
  <el-radio-button label="piece">按个</el-radio-button>
  <el-radio-button label="box">按箱（参考）</el-radio-button>
</el-radio-group>
```

在 `data()` 加 `displayMode: 'piece'`，在 `methods` 加：
```js
fmtQty(val, productId) {
  if (this.displayMode === 'piece' || !val) return val
  const prod = this.productMap?.[productId]
  const q = prod?.qtyPerBox
  if (!q) return val + '(箱?)'
  return Math.floor(val / q) + '箱' + (val % q ? ' ' + val % q + '个' : '')
}
```

数量列的渲染改为：
```html
<template slot-scope="{row}">{{ fmtQty(row.inQty, row.productId) }}</template>
```

> 注意：`productMap` 需要在该页面加载，方法同库存列表页。若已有可复用的全局 store 则直接使用。

- [ ] **Step 2: Inventory 报表加箱/个切换**

打开 `src/views/report/Inventory.vue`，用相同方式加 `displayMode` 和 `fmtQty`。

- [ ] **Step 3: 验证**

1. 打开进退存汇总，切换为「按箱」，确认已设 qtyPerBox 的商品显示箱数，未设的显示「X(箱?)」
2. 打开库存价值表，同样验证

- [ ] **Step 4: Commit**

```bash
cd D:/AI/warehouse-frontend
git add src/views/report/StockMovement.vue src/views/report/Inventory.vue
git commit -m "feat: reports add box/piece display toggle"
```

---

## 风险备忘

1. **并发安全**：Task 5 的 `transfer()` 方法已用 `selectOneForUpdate` 加行锁，`@Transactional` 包裹整个事务，安全。
2. **ReportServiceImpl 风格**：写 Task 8 前先看 `ReportServiceImpl.java` 用的是 `JdbcTemplate` 还是 `NamedParameterJdbcTemplate`，保持一致。
3. **qtyPerBox=1 的情况**：Task 4 中 `qtyPerBox` 默认为 1 时不进行价格换算，代码已用 `qtyPerBox > 1` 判断。
4. **菜单导航**：Task 8 中加报表菜单项需找到侧边栏组件的实际文件（可能是 `Layout/Sidebar.vue` 或 `Layout/index.vue`），确认后再加。