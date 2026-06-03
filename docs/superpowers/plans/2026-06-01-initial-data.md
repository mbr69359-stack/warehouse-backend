# 杯具仓储系统初始数据 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 向 MySQL 数据库写入完整的杯具仓储初始数据——3个仓库、3家供应商、5个分类、20个商品SKU、3179先令库存（含4款预警）、20笔历史入库单、25笔历史出库单，使仪表盘和报表有真实数据显示。

**Architecture:** 仓库/供应商/分类/商品/库存追加到 `init.sql`（保持幂等性），历史订单单独写入 `seed-orders.sql`（一次性执行）。两个文件都通过 SSH 在 VPS 上的 MySQL 中直接执行。报表后端（3个接口）已实现无需修改。

**Tech Stack:** MySQL 8, SSH, Spring Boot（VPS: 139.84.247.83，MySQL 用户: warehouse）

---

## File Map

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/main/resources/sql/init.sql` | 追加 | 幂等：仓库/供应商/分类/商品/库存 |
| `src/main/resources/sql/seed-orders.sql` | 新建 | 一次性：20笔入库单 + 25笔出库单 |

---

### Task 1: 仓库 & 供应商

**Files:**
- Modify: `src/main/resources/sql/init.sql`

- [ ] **Step 1: 在 init.sql 末尾追加以下 SQL**

```sql
-- ================================================================
-- Cup Warehouse Seed Data (2026-06-01)
-- ================================================================

-- 仓库：更新原"主仓库"为华东仓库，新增华南/华北
UPDATE warehouse SET name='华东仓库', address='上海市松江区' WHERE id=1 AND deleted=0;

INSERT INTO warehouse (id, name, address, manager_id, status) VALUES
(2, '华南仓库', '广州市番禺区', 1, 1),
(3, '华北仓库', '北京市通州区', 1, 1)
ON DUPLICATE KEY UPDATE id=id;

-- 供应商
INSERT INTO supplier (id, name, status) VALUES
(1, '景德镇陶瓷杯业有限公司', 1),
(2, '浙江晶艺玻璃制品有限公司', 1),
(3, '广州保温制品贸易有限公司', 1)
ON DUPLICATE KEY UPDATE name=VALUES(name);
```

- [ ] **Step 2: 提交**

```bash
git add src/main/resources/sql/init.sql
git commit -m "seed: add cup warehouses and suppliers"
```

---

### Task 2: 分类 & 商品（20 SKU）

**Files:**
- Modify: `src/main/resources/sql/init.sql`

- [ ] **Step 1: 在 init.sql 末尾继续追加**

```sql
-- 分类：将通用分类替换为杯具专属分类
UPDATE category SET name='陶瓷杯', sort=1 WHERE name='电子产品' AND deleted=0;
UPDATE category SET name='玻璃杯', sort=2 WHERE name='服装'     AND deleted=0;
UPDATE category SET name='保温杯', sort=3 WHERE name='食品'     AND deleted=0;

INSERT INTO category (name, sort)
SELECT '马克杯', 4 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='马克杯' AND deleted=0);

INSERT INTO category (name, sort)
SELECT '定制杯', 5 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='定制杯' AND deleted=0);

-- 商品（20 SKU，单位：先令）
INSERT INTO product (sku_code, name, category_id, unit, price, status) VALUES
('CUP-CER-001','白瓷直筒杯',    (SELECT id FROM category WHERE name='陶瓷杯' AND deleted=0 LIMIT 1),'先令',180.00,1),
('CUP-CER-002','青花瓷茶杯',    (SELECT id FROM category WHERE name='陶瓷杯' AND deleted=0 LIMIT 1),'先令',260.00,1),
('CUP-CER-003','红釉咖啡杯',    (SELECT id FROM category WHERE name='陶瓷杯' AND deleted=0 LIMIT 1),'先令',220.00,1),
('CUP-CER-004','哑光陶瓷杯',    (SELECT id FROM category WHERE name='陶瓷杯' AND deleted=0 LIMIT 1),'先令',195.00,1),
('CUP-GLS-001','高硼硅玻璃杯',  (SELECT id FROM category WHERE name='玻璃杯' AND deleted=0 LIMIT 1),'先令',240.00,1),
('CUP-GLS-002','双层玻璃杯',    (SELECT id FROM category WHERE name='玻璃杯' AND deleted=0 LIMIT 1),'先令',320.00,1),
('CUP-GLS-003','玻璃冷水壶',    (SELECT id FROM category WHERE name='玻璃杯' AND deleted=0 LIMIT 1),'先令',280.00,1),
('CUP-TRM-001','304不锈钢保温杯',(SELECT id FROM category WHERE name='保温杯' AND deleted=0 LIMIT 1),'先令',380.00,1),
('CUP-TRM-002','真空保温水壶',  (SELECT id FROM category WHERE name='保温杯' AND deleted=0 LIMIT 1),'先令',420.00,1),
('CUP-TRM-003','316不锈钢保温杯',(SELECT id FROM category WHERE name='保温杯' AND deleted=0 LIMIT 1),'先令',460.00,1),
('CUP-TRM-004','保温咖啡杯',    (SELECT id FROM category WHERE name='保温杯' AND deleted=0 LIMIT 1),'先令',350.00,1),
('CUP-MUG-001','大容量马克杯',  (SELECT id FROM category WHERE name='马克杯' AND deleted=0 LIMIT 1),'先令',160.00,1),
('CUP-MUG-002','带盖马克杯',    (SELECT id FROM category WHERE name='马克杯' AND deleted=0 LIMIT 1),'先令',190.00,1),
('CUP-MUG-003','卡通印花马克杯',(SELECT id FROM category WHERE name='马克杯' AND deleted=0 LIMIT 1),'先令',175.00,1),
('CUP-MUG-004','简约商务马克杯',(SELECT id FROM category WHERE name='马克杯' AND deleted=0 LIMIT 1),'先令',210.00,1),
('CUP-CST-001','企业logo定制杯',(SELECT id FROM category WHERE name='定制杯' AND deleted=0 LIMIT 1),'先令',500.00,1),
('CUP-CST-002','礼盒套装定制杯',(SELECT id FROM category WHERE name='定制杯' AND deleted=0 LIMIT 1),'先令',680.00,1),
('CUP-CST-003','婚庆纪念定制杯',(SELECT id FROM category WHERE name='定制杯' AND deleted=0 LIMIT 1),'先令',580.00,1),
('CUP-CST-004','节日礼品定制杯',(SELECT id FROM category WHERE name='定制杯' AND deleted=0 LIMIT 1),'先令',620.00,1),
('CUP-CST-005','学校活动定制杯',(SELECT id FROM category WHERE name='定制杯' AND deleted=0 LIMIT 1),'先令',450.00,1)
ON DUPLICATE KEY UPDATE name=VALUES(name), category_id=VALUES(category_id), unit=VALUES(unit), price=VALUES(price);
```

- [ ] **Step 2: 提交**

```bash
git add src/main/resources/sql/init.sql
git commit -m "seed: add cup categories and 20 product SKUs"
```

---

### Task 3: 库存（3179先令，含4款预警）

**Files:**
- Modify: `src/main/resources/sql/init.sql`

- [ ] **Step 1: 在 init.sql 末尾追加库存数据**

```sql
-- 库存（华东1443 + 华南763 + 华北523 = 3179先令）
-- 预警商品：CUP-CER-003、CUP-MUG-002、CUP-CST-002、CUP-CST-004
INSERT INTO inventory (warehouse_id, product_id, qty, alert_qty) VALUES
-- 华东仓库
(1,(SELECT id FROM product WHERE sku_code='CUP-CER-001'),120,50),
(1,(SELECT id FROM product WHERE sku_code='CUP-CER-002'),80,30),
(1,(SELECT id FROM product WHERE sku_code='CUP-CER-003'),8,30),
(1,(SELECT id FROM product WHERE sku_code='CUP-CER-004'),90,40),
(1,(SELECT id FROM product WHERE sku_code='CUP-GLS-001'),100,50),
(1,(SELECT id FROM product WHERE sku_code='CUP-GLS-002'),70,30),
(1,(SELECT id FROM product WHERE sku_code='CUP-GLS-003'),60,25),
(1,(SELECT id FROM product WHERE sku_code='CUP-TRM-001'),150,60),
(1,(SELECT id FROM product WHERE sku_code='CUP-TRM-002'),120,50),
(1,(SELECT id FROM product WHERE sku_code='CUP-TRM-003'),90,40),
(1,(SELECT id FROM product WHERE sku_code='CUP-TRM-004'),110,50),
(1,(SELECT id FROM product WHERE sku_code='CUP-MUG-001'),130,60),
(1,(SELECT id FROM product WHERE sku_code='CUP-MUG-002'),12,40),
(1,(SELECT id FROM product WHERE sku_code='CUP-MUG-003'),100,40),
(1,(SELECT id FROM product WHERE sku_code='CUP-MUG-004'),85,35),
(1,(SELECT id FROM product WHERE sku_code='CUP-CST-001'),60,25),
(1,(SELECT id FROM product WHERE sku_code='CUP-CST-002'),10,30),
(1,(SELECT id FROM product WHERE sku_code='CUP-CST-003'),40,20),
(1,(SELECT id FROM product WHERE sku_code='CUP-CST-004'),8,25),
(1,(SELECT id FROM product WHERE sku_code='CUP-CST-005'),50,20),
-- 华南仓库
(2,(SELECT id FROM product WHERE sku_code='CUP-CER-001'),60,50),
(2,(SELECT id FROM product WHERE sku_code='CUP-CER-002'),40,30),
(2,(SELECT id FROM product WHERE sku_code='CUP-CER-003'),5,30),
(2,(SELECT id FROM product WHERE sku_code='CUP-CER-004'),50,40),
(2,(SELECT id FROM product WHERE sku_code='CUP-GLS-001'),55,50),
(2,(SELECT id FROM product WHERE sku_code='CUP-GLS-002'),35,30),
(2,(SELECT id FROM product WHERE sku_code='CUP-GLS-003'),30,25),
(2,(SELECT id FROM product WHERE sku_code='CUP-TRM-001'),80,60),
(2,(SELECT id FROM product WHERE sku_code='CUP-TRM-002'),60,50),
(2,(SELECT id FROM product WHERE sku_code='CUP-TRM-003'),45,40),
(2,(SELECT id FROM product WHERE sku_code='CUP-TRM-004'),55,50),
(2,(SELECT id FROM product WHERE sku_code='CUP-MUG-001'),70,60),
(2,(SELECT id FROM product WHERE sku_code='CUP-MUG-002'),8,40),
(2,(SELECT id FROM product WHERE sku_code='CUP-MUG-003'),50,40),
(2,(SELECT id FROM product WHERE sku_code='CUP-MUG-004'),40,35),
(2,(SELECT id FROM product WHERE sku_code='CUP-CST-001'),30,25),
(2,(SELECT id FROM product WHERE sku_code='CUP-CST-002'),5,30),
(2,(SELECT id FROM product WHERE sku_code='CUP-CST-003'),20,20),
(2,(SELECT id FROM product WHERE sku_code='CUP-CST-004'),0,25),
(2,(SELECT id FROM product WHERE sku_code='CUP-CST-005'),25,20),
-- 华北仓库
(3,(SELECT id FROM product WHERE sku_code='CUP-CER-001'),40,50),
(3,(SELECT id FROM product WHERE sku_code='CUP-CER-002'),30,30),
(3,(SELECT id FROM product WHERE sku_code='CUP-CER-003'),0,30),
(3,(SELECT id FROM product WHERE sku_code='CUP-CER-004'),35,40),
(3,(SELECT id FROM product WHERE sku_code='CUP-GLS-001'),40,50),
(3,(SELECT id FROM product WHERE sku_code='CUP-GLS-002'),25,30),
(3,(SELECT id FROM product WHERE sku_code='CUP-GLS-003'),20,25),
(3,(SELECT id FROM product WHERE sku_code='CUP-TRM-001'),60,60),
(3,(SELECT id FROM product WHERE sku_code='CUP-TRM-002'),40,50),
(3,(SELECT id FROM product WHERE sku_code='CUP-TRM-003'),30,40),
(3,(SELECT id FROM product WHERE sku_code='CUP-TRM-004'),35,50),
(3,(SELECT id FROM product WHERE sku_code='CUP-MUG-001'),50,60),
(3,(SELECT id FROM product WHERE sku_code='CUP-MUG-002'),0,40),
(3,(SELECT id FROM product WHERE sku_code='CUP-MUG-003'),35,40),
(3,(SELECT id FROM product WHERE sku_code='CUP-MUG-004'),30,35),
(3,(SELECT id FROM product WHERE sku_code='CUP-CST-001'),20,25),
(3,(SELECT id FROM product WHERE sku_code='CUP-CST-002'),0,30),
(3,(SELECT id FROM product WHERE sku_code='CUP-CST-003'),15,20),
(3,(SELECT id FROM product WHERE sku_code='CUP-CST-004'),0,25),
(3,(SELECT id FROM product WHERE sku_code='CUP-CST-005'),18,20)
ON DUPLICATE KEY UPDATE qty=VALUES(qty), alert_qty=VALUES(alert_qty);
```

- [ ] **Step 2: 提交**

```bash
git add src/main/resources/sql/init.sql
git commit -m "seed: add inventory (3179 先令, 4 alert SKUs)"
```

---

### Task 4: 历史订单（20笔入库 + 25笔出库）

**Files:**
- Create: `src/main/resources/sql/seed-orders.sql`

> **注意：** 此文件仅在 VPS 上执行一次。使用 INSERT IGNORE 保证重复执行安全。

- [ ] **Step 1: 创建 seed-orders.sql，写入完整内容**

```sql
-- ================================================================
-- Historical Orders Seed (20 in-orders + 25 out-orders)
-- 执行一次即可。order_no 唯一，重复执行安全。
-- ================================================================

-- 缓存商品ID（按SKU查询，不依赖自增ID值）
SET @p1  = (SELECT id FROM product WHERE sku_code='CUP-CER-001');
SET @p2  = (SELECT id FROM product WHERE sku_code='CUP-CER-002');
SET @p3  = (SELECT id FROM product WHERE sku_code='CUP-CER-003');
SET @p4  = (SELECT id FROM product WHERE sku_code='CUP-CER-004');
SET @p5  = (SELECT id FROM product WHERE sku_code='CUP-GLS-001');
SET @p6  = (SELECT id FROM product WHERE sku_code='CUP-GLS-002');
SET @p7  = (SELECT id FROM product WHERE sku_code='CUP-GLS-003');
SET @p8  = (SELECT id FROM product WHERE sku_code='CUP-TRM-001');
SET @p9  = (SELECT id FROM product WHERE sku_code='CUP-TRM-002');
SET @p10 = (SELECT id FROM product WHERE sku_code='CUP-TRM-003');
SET @p11 = (SELECT id FROM product WHERE sku_code='CUP-TRM-004');
SET @p12 = (SELECT id FROM product WHERE sku_code='CUP-MUG-001');
SET @p13 = (SELECT id FROM product WHERE sku_code='CUP-MUG-002');
SET @p14 = (SELECT id FROM product WHERE sku_code='CUP-MUG-003');
SET @p15 = (SELECT id FROM product WHERE sku_code='CUP-MUG-004');
SET @p16 = (SELECT id FROM product WHERE sku_code='CUP-CST-001');
SET @p17 = (SELECT id FROM product WHERE sku_code='CUP-CST-002');
SET @p18 = (SELECT id FROM product WHERE sku_code='CUP-CST-003');
SET @p19 = (SELECT id FROM product WHERE sku_code='CUP-CST-004');
SET @p20 = (SELECT id FROM product WHERE sku_code='CUP-CST-005');

-- ── 入库单（20笔）────────────────────────────────────────────────

-- IN-01: 2026-05-02 华东 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605020001',1,1,'PURCHASE','CONFIRMED',1,'2026-05-02 09:00:00','2026-05-02 14:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605020001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p1,30,30,180.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p2,20,20,260.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p2);

-- IN-02: 2026-05-04 华南 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605040001',2,1,'PURCHASE','CONFIRMED',1,'2026-05-04 10:00:00','2026-05-04 15:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605040001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p1,15,15,180.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p12,25,25,160.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p12);

-- IN-03: 2026-05-06 华东 浙江
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605060001',1,2,'PURCHASE','CONFIRMED',1,'2026-05-06 09:30:00','2026-05-06 14:30:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605060001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p5,20,20,240.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p5);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p6,15,15,320.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p6);

-- IN-04: 2026-05-07 华北 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605070001',3,1,'PURCHASE','CONFIRMED',1,'2026-05-07 08:30:00','2026-05-07 13:30:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605070001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p4,18,18,195.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p4);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p14,22,22,175.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p14);

-- IN-05: 2026-05-09 华东 广州
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605090001',1,3,'PURCHASE','CONFIRMED',1,'2026-05-09 10:00:00','2026-05-09 16:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605090001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p8,25,25,380.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p8);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p9,20,20,420.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p9);

-- IN-06: 2026-05-10 华南 浙江
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605100001',2,2,'PURCHASE','CONFIRMED',1,'2026-05-10 09:00:00','2026-05-10 14:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605100001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p5,18,18,240.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p5);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p7,12,12,280.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p7);

-- IN-07: 2026-05-12 华东 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605120001',1,1,'PURCHASE','CONFIRMED',1,'2026-05-12 10:30:00','2026-05-12 15:30:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605120001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p1,40,40,180.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p13,10,10,190.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p13);

-- IN-08: 2026-05-13 华北 广州
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605130001',3,3,'PURCHASE','CONFIRMED',1,'2026-05-13 09:00:00','2026-05-13 14:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605130001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p10,15,15,460.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p10);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p11,20,20,350.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p11);

-- IN-09: 2026-05-15 华东 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605150001',1,1,'PURCHASE','CONFIRMED',1,'2026-05-15 08:00:00','2026-05-15 13:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605150001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p12,30,30,160.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p12);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p15,18,18,210.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p15);

-- IN-10: 2026-05-16 华南 广州
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605160001',2,3,'PURCHASE','CONFIRMED',1,'2026-05-16 10:00:00','2026-05-16 15:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605160001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p8,20,20,380.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p8);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p9,15,15,420.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p9);

-- IN-11: 2026-05-18 华东 浙江
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605180001',1,2,'PURCHASE','CONFIRMED',1,'2026-05-18 09:30:00','2026-05-18 14:30:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605180001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p6,20,20,320.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p6);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p7,15,15,280.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p7);

-- IN-12: 2026-05-19 华东 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605190001',1,1,'PURCHASE','CONFIRMED',1,'2026-05-19 10:00:00','2026-05-19 15:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605190001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p16,10,10,500.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p16);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p18,8,8,580.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p18);

-- IN-13: 2026-05-20 华南 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605200001',2,1,'PURCHASE','CONFIRMED',1,'2026-05-20 09:00:00','2026-05-20 14:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605200001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p1,22,22,180.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p4,18,18,195.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p4);

-- IN-14: 2026-05-21 华北 广州
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605210001',3,3,'PURCHASE','CONFIRMED',1,'2026-05-21 08:30:00','2026-05-21 13:30:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605210001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p8,20,20,380.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p8);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p11,15,15,350.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p11);

-- IN-15: 2026-05-22 华东 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605220001',1,1,'PURCHASE','CONFIRMED',1,'2026-05-22 10:00:00','2026-05-22 15:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605220001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p14,25,25,175.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p14);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p15,20,20,210.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p15);

-- IN-16: 2026-05-23 华东 浙江
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605230001',1,2,'PURCHASE','CONFIRMED',1,'2026-05-23 09:00:00','2026-05-23 14:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605230001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p5,25,25,240.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p5);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p6,18,18,320.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p6);

-- IN-17: 2026-05-25 华南 广州
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605250001',2,3,'PURCHASE','CONFIRMED',1,'2026-05-25 10:00:00','2026-05-25 15:30:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605250001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p9,18,18,420.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p9);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p10,12,12,460.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p10);

-- IN-18: 2026-05-27 华东 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605270001',1,1,'PURCHASE','CONFIRMED',1,'2026-05-27 09:00:00','2026-05-27 14:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605270001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p1,35,35,180.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p2,22,22,260.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p2);

-- IN-19: 2026-05-29 华北 景德镇
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605290001',3,1,'PURCHASE','CONFIRMED',1,'2026-05-29 08:00:00','2026-05-29 13:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605290001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p4,20,20,195.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p4);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p12,28,28,160.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p12);

-- IN-20: 2026-05-31 华东 浙江
INSERT IGNORE INTO in_order (order_no,warehouse_id,supplier_id,type,status,operator_id,create_time,confirm_time)
VALUES ('IN202605310001',1,2,'PURCHASE','CONFIRMED',1,'2026-05-31 10:00:00','2026-05-31 15:00:00');
SET @oid = (SELECT id FROM in_order WHERE order_no='IN202605310001');
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p5,30,30,240.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p5);
INSERT INTO in_order_item (order_id,product_id,plan_qty,actual_qty,price)
SELECT @oid,@p6,22,22,320.00 WHERE NOT EXISTS(SELECT 1 FROM in_order_item WHERE order_id=@oid AND product_id=@p6);

-- ── 出库单（25笔）────────────────────────────────────────────────

-- OUT-01: 2026-05-03 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605030001',1,'SALE','CONFIRMED',1,'2026-05-03 10:00:00','2026-05-03 14:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605030001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p1,10,180.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p12,15,160.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p12);

-- OUT-02: 2026-05-04 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605040001',1,'SALE','CONFIRMED',1,'2026-05-04 11:00:00','2026-05-04 15:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605040001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p5,12,240.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p5);

-- OUT-03: 2026-05-05 华南
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605050001',2,'SALE','CONFIRMED',1,'2026-05-05 09:00:00','2026-05-05 13:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605050001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p2,8,260.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p2);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p4,10,195.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p4);

-- OUT-04: 2026-05-07 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605070001',1,'SALE','CONFIRMED',1,'2026-05-07 10:00:00','2026-05-07 15:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605070001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p8,15,380.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p8);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p9,10,420.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p9);

-- OUT-05: 2026-05-08 华北
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605080001',3,'SALE','CONFIRMED',1,'2026-05-08 09:00:00','2026-05-08 13:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605080001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p14,12,175.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p14);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p4,8,195.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p4);

-- OUT-06: 2026-05-09 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605090001',1,'SALE','CONFIRMED',1,'2026-05-09 11:00:00','2026-05-09 16:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605090001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p6,10,320.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p6);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p7,8,280.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p7);

-- OUT-07: 2026-05-11 华南
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605110001',2,'SALE','CONFIRMED',1,'2026-05-11 10:00:00','2026-05-11 14:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605110001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p1,15,180.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p12,12,160.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p12);

-- OUT-08: 2026-05-12 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605120001',1,'SALE','CONFIRMED',1,'2026-05-12 09:00:00','2026-05-12 13:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605120001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p8,20,380.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p8);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p11,12,350.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p11);

-- OUT-09: 2026-05-13 华北
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605130001',3,'SALE','CONFIRMED',1,'2026-05-13 10:00:00','2026-05-13 15:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605130001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p4,10,195.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p4);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p15,8,210.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p15);

-- OUT-10: 2026-05-14 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605140001',1,'SALE','CONFIRMED',1,'2026-05-14 09:30:00','2026-05-14 14:30:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605140001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p5,15,240.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p5);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p6,10,320.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p6);

-- OUT-11: 2026-05-15 华南
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605150001',2,'SALE','CONFIRMED',1,'2026-05-15 10:00:00','2026-05-15 15:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605150001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p9,12,420.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p9);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p10,8,460.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p10);

-- OUT-12: 2026-05-16 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605160001',1,'SALE','CONFIRMED',1,'2026-05-16 09:00:00','2026-05-16 14:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605160001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p1,20,180.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p2,15,260.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p2);

-- OUT-13: 2026-05-17 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605170001',1,'SALE','CONFIRMED',1,'2026-05-17 10:30:00','2026-05-17 15:30:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605170001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p12,18,160.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p12);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p14,12,175.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p14);

-- OUT-14: 2026-05-19 华南
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605190001',2,'SALE','CONFIRMED',1,'2026-05-19 09:00:00','2026-05-19 13:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605190001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p5,12,240.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p5);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p7,8,280.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p7);

-- OUT-15: 2026-05-20 华北
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605200001',3,'SALE','CONFIRMED',1,'2026-05-20 10:00:00','2026-05-20 15:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605200001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p8,15,380.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p8);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p11,10,350.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p11);

-- OUT-16: 2026-05-21 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605210001',1,'SALE','CONFIRMED',1,'2026-05-21 09:30:00','2026-05-21 14:30:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605210001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p1,25,180.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p15,15,210.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p15);

-- OUT-17: 2026-05-22 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605220001',1,'SALE','CONFIRMED',1,'2026-05-22 10:00:00','2026-05-22 15:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605220001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p6,12,320.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p6);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p7,10,280.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p7);

-- OUT-18: 2026-05-23 华南
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605230001',2,'SALE','CONFIRMED',1,'2026-05-23 09:00:00','2026-05-23 14:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605230001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p9,15,420.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p9);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p10,10,460.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p10);

-- OUT-19: 2026-05-24 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605240001',1,'SALE','CONFIRMED',1,'2026-05-24 10:30:00','2026-05-24 15:30:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605240001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p1,20,180.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p1);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p4,15,195.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p4);

-- OUT-20: 2026-05-25 华北
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605250001',3,'SALE','CONFIRMED',1,'2026-05-25 09:00:00','2026-05-25 14:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605250001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p12,20,160.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p12);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p13,12,190.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p13);

-- OUT-21: 2026-05-26 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605260001',1,'SALE','CONFIRMED',1,'2026-05-26 10:00:00','2026-05-26 15:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605260001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p5,18,240.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p5);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p8,12,380.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p8);

-- OUT-22: 2026-05-27 华南
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605270001',2,'SALE','CONFIRMED',1,'2026-05-27 09:30:00','2026-05-27 14:30:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605270001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p2,10,260.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p2);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p4,12,195.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p4);

-- OUT-23: 2026-05-28 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605280001',1,'SALE','CONFIRMED',1,'2026-05-28 10:00:00','2026-05-28 15:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605280001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p9,15,420.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p9);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p14,10,175.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p14);

-- OUT-24: 2026-05-29 华东
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605290001',1,'SALE','CONFIRMED',1,'2026-05-29 09:00:00','2026-05-29 14:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605290001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p6,12,320.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p6);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p1,18,180.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p1);

-- OUT-25: 2026-05-31 华北
INSERT IGNORE INTO out_order (order_no,warehouse_id,type,status,operator_id,create_time,confirm_time)
VALUES ('OUT202605310001',3,'SALE','CONFIRMED',1,'2026-05-31 10:00:00','2026-05-31 15:00:00');
SET @oid = (SELECT id FROM out_order WHERE order_no='OUT202605310001');
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p8,15,380.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p8);
INSERT INTO out_order_item (order_id,product_id,qty,price)
SELECT @oid,@p15,10,210.00 WHERE NOT EXISTS(SELECT 1 FROM out_order_item WHERE order_id=@oid AND product_id=@p15);
```

- [ ] **Step 2: 提交**

```bash
git add src/main/resources/sql/seed-orders.sql
git commit -m "seed: add 20 in-orders and 25 out-orders for report data"
```

---

### Task 5: 部署到 VPS 并验证

**Files:** 无（在 VPS 上执行 SQL）

- [ ] **Step 1: 将两个 SQL 文件上传到 VPS**

```bash
scp src/main/resources/sql/init.sql root@139.84.247.83:/tmp/init.sql
scp src/main/resources/sql/seed-orders.sql root@139.84.247.83:/tmp/seed-orders.sql
```

- [ ] **Step 2: SSH 连接 VPS，执行 init.sql（幂等，安全重复运行）**

```bash
ssh root@139.84.247.83
mysql -u warehouse -pWH_DB_Pass_2026! warehouse < /tmp/init.sql
```

预期输出：无错误（有 warning 是正常的，比如 UPDATE 0 rows）

- [ ] **Step 3: 执行 seed-orders.sql（一次性）**

```bash
mysql -u warehouse -pWH_DB_Pass_2026! warehouse < /tmp/seed-orders.sql
```

预期输出：无错误

- [ ] **Step 4: 验证数据**

```bash
mysql -u warehouse -pWH_DB_Pass_2026! warehouse -e "
SELECT '仓库' AS tbl, COUNT(*) AS cnt FROM warehouse WHERE deleted=0
UNION SELECT '供应商', COUNT(*) FROM supplier WHERE deleted=0
UNION SELECT '分类', COUNT(*) FROM category WHERE deleted=0
UNION SELECT '商品', COUNT(*) FROM product WHERE deleted=0
UNION SELECT '库存记录', COUNT(*) FROM inventory
UNION SELECT '入库单', COUNT(*) FROM in_order WHERE status='CONFIRMED'
UNION SELECT '出库单', COUNT(*) FROM out_order WHERE status='CONFIRMED'
UNION SELECT '预警商品', COUNT(*) FROM inventory WHERE qty < alert_qty AND alert_qty > 0;
"
```

预期结果：

```
仓库      3
供应商    3
分类      5
商品      20
库存记录  60
入库单    20
出库单    25
预警商品  4+（各仓库分开统计）
```

- [ ] **Step 5: 打开浏览器验证（桌面端）**

访问 https://xiaocup.com，使用 admin/admin123 登录，检查：
- 仪表盘：4个数字卡片均有数值，库存预警卡片显示预警数
- 入库报表：选择2026-05-01 至 2026-05-31，图表显示20个数据点
- 出库报表：同上，显示25个数据点
- 库存报表：显示 SKU数=20，总库存=3179，预警数>0

- [ ] **Step 6: 清理 VPS 临时文件**

```bash
rm /tmp/init.sql /tmp/seed-orders.sql
```

---

## 验收标准

| 检查项 | 预期 |
|--------|------|
| 仓库数 | 3（华东/华南/华北）|
| 供应商数 | 3 |
| 分类 | 5个杯具专属分类 |
| 商品SKU | 20个，单位全部为"先令" |
| 总库存 | 3179先令 |
| 预警商品 | 4款（红釉咖啡杯/带盖马克杯/礼盒套装定制杯/节日礼品定制杯）|
| 入库报表图表 | 5月份有曲线，非空 |
| 出库报表图表 | 5月份有曲线，非空 |