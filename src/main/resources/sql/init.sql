CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    real_name   VARCHAR(50),
    phone       VARCHAR(20),
    email       VARCHAR(100),
    status      SMALLINT NOT NULL DEFAULT 1,
    deleted     SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(50)  NOT NULL,
    role_code   VARCHAR(50)  NOT NULL UNIQUE,
    remark      VARCHAR(200),
    deleted     SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id   BIGINT NOT NULL DEFAULT 0,
    menu_name   VARCHAR(50)  NOT NULL,
    path        VARCHAR(200),
    component   VARCHAR(200),
    icon        VARCHAR(100),
    sort        INT NOT NULL DEFAULT 0,
    menu_type   CHAR(1) NOT NULL,
    perms       VARCHAR(100),
    visible     SMALLINT NOT NULL DEFAULT 1,
    deleted     SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE IF NOT EXISTS warehouse (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(200),
    manager_id  BIGINT,
    status      SMALLINT NOT NULL DEFAULT 1,
    remark      VARCHAR(200),
    deleted     SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS category (
    id        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name      VARCHAR(100) NOT NULL,
    sort      INT NOT NULL DEFAULT 0,
    deleted   SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS product (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    sku_code    VARCHAR(100) NOT NULL UNIQUE,
    category_id BIGINT,
    unit        VARCHAR(20)  NOT NULL DEFAULT '个',
    price       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    image       VARCHAR(500),
    remark      VARCHAR(500),
    status      SMALLINT NOT NULL DEFAULT 1,
    deleted     SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS supplier (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    contact     VARCHAR(50),
    phone       VARCHAR(20),
    email       VARCHAR(100),
    address     VARCHAR(300),
    status      SMALLINT NOT NULL DEFAULT 1,
    user_id     BIGINT,
    deleted     SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS in_order (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no     VARCHAR(30)  NOT NULL UNIQUE,
    warehouse_id BIGINT NOT NULL,
    supplier_id  BIGINT,
    type         VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    operator_id  BIGINT NOT NULL,
    remark       VARCHAR(500),
    deleted      SMALLINT NOT NULL DEFAULT 0,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_time TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS in_order_item (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    plan_qty   INT NOT NULL DEFAULT 0,
    actual_qty INT NOT NULL DEFAULT 0,
    price      DECIMAL(12,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE IF NOT EXISTS out_order (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no     VARCHAR(30)  NOT NULL UNIQUE,
    warehouse_id BIGINT NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    operator_id  BIGINT NOT NULL,
    remark       VARCHAR(500),
    deleted      SMALLINT NOT NULL DEFAULT 0,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_time        TIMESTAMP NULL,
    target_warehouse_id BIGINT NULL
);

CREATE TABLE IF NOT EXISTS out_order_item (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    qty        INT NOT NULL DEFAULT 0,
    actual_qty INT NOT NULL DEFAULT 0,
    price      DECIMAL(12,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE IF NOT EXISTS inventory (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    product_id   BIGINT NOT NULL,
    qty          INT NOT NULL DEFAULT 0 CHECK (qty >= 0),
    alert_qty    INT NOT NULL DEFAULT 0,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_inventory (warehouse_id, product_id)
);

CREATE TABLE IF NOT EXISTS inventory_log (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    product_id   BIGINT NOT NULL,
    change_qty   INT NOT NULL,
    before_qty   INT NOT NULL,
    after_qty    INT NOT NULL,
    type         VARCHAR(20) NOT NULL,
    ref_order_id BIGINT,
    remark       VARCHAR(200),
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 初始数据
INSERT INTO sys_role (role_name, role_code, remark) VALUES
('管理员', 'ADMIN', '拥有全部权限'),
('仓库员工', 'STAFF', '入库出库库存操作'),
('供应商', 'SUPPLIER', '查看自己的入库单')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name);

-- 默认管理员 密码: admin123
INSERT INTO sys_user (id, username, password, real_name, status) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyU8XIVWK', '系统管理员', 1)
ON DUPLICATE KEY UPDATE id=id;

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

INSERT INTO warehouse (id, name, address, manager_id, status) VALUES
(1, '主仓库', '默认地址', 1, 1)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO category (parent_id, name, sort)
SELECT t.* FROM (SELECT 0 AS parent_id, '电子产品' AS name, 1 AS sort) AS t
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='电子产品' AND parent_id=0 AND deleted=0);

INSERT INTO category (parent_id, name, sort)
SELECT t.* FROM (SELECT 0 AS parent_id, '服装' AS name, 2 AS sort) AS t
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='服装' AND parent_id=0 AND deleted=0);

INSERT INTO category (parent_id, name, sort)
SELECT t.* FROM (SELECT 0 AS parent_id, '食品' AS name, 3 AS sort) AS t
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='食品' AND parent_id=0 AND deleted=0);

-- =============================================
-- 追加：仓库 & 供应商
-- =============================================
INSERT INTO warehouse (id, name, address, manager_id, status) VALUES
(2, '华南分仓', '广州市天河区科韵路15号', 1, 1),
(3, '华东分仓', '上海市浦东新区张江路88号', 1, 1)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO supplier (id, name, contact, phone, email, address, status) VALUES
(1, '深圳华为科技供应商', '张伟', '13800138001', 'zhangwei@huawei.com', '广东省深圳市龙岗区坂田华为基地', 1),
(2, '北京联想集团',       '李娜', '13900139001', 'lina@lenovo.com',     '北京市海淀区清河中街68号',         1),
(3, '上海优衣库贸易',     '王芳', '13700137001', 'wangfang@uniqlo.com', '上海市静安区静安寺路1号',           1),
(4, '台湾旺旺食品',       '陈明', '13600136001', 'chenming@want-want.com', '上海市闵行区莘松路30号',         1),
(5, '南京得力文具',       '刘洋', '13500135001', 'liuyang@deli.com',    '江苏省南京市江宁区弘景大道',         1)
ON DUPLICATE KEY UPDATE id=id;

-- =============================================
-- 追加：办公用品分类 & 20个商品SKU
-- =============================================
INSERT INTO category (id, parent_id, name, sort) VALUES
(4, 0, '办公用品', 4)
ON DUPLICATE KEY UPDATE name=VALUES(name);

SET @cat_elec   = (SELECT id FROM category WHERE name='电子产品' AND deleted=0 LIMIT 1);
SET @cat_cloth  = (SELECT id FROM category WHERE name='服装'     AND deleted=0 LIMIT 1);
SET @cat_food   = (SELECT id FROM category WHERE name='食品'     AND deleted=0 LIMIT 1);
SET @cat_office = (SELECT id FROM category WHERE name='办公用品' AND deleted=0 LIMIT 1);

INSERT INTO product (id, name, sku_code, category_id, unit, price, status) VALUES
(1,  'iPhone 15 128GB',           'SKU-ELEC-001', @cat_elec,   '台',  5999.00, 1),
(2,  '华为Mate60 Pro 256GB',       'SKU-ELEC-002', @cat_elec,   '台',  6999.00, 1),
(3,  '小米14 Pro 256GB',           'SKU-ELEC-003', @cat_elec,   '台',  3999.00, 1),
(4,  'MacBook Air M3 13英寸',      'SKU-ELEC-004', @cat_elec,   '台',  9999.00, 1),
(5,  '联想ThinkPad X1 Carbon',     'SKU-ELEC-005', @cat_elec,   '台', 12999.00, 1),
(6,  'iPad Air M2 11英寸',         'SKU-ELEC-006', @cat_elec,   '台',  4799.00, 1),
(7,  'Sony WH-1000XM5 耳机',       'SKU-ELEC-007', @cat_elec,   '副',  2299.00, 1),
(8,  '小米手环8 Pro',              'SKU-ELEC-008', @cat_elec,   '条',   299.00, 1),
(9,  '优衣库UT短袖T恤',            'SKU-CLTH-001', @cat_cloth,  '件',    79.00, 1),
(10, '优衣库轻型羽绒服',           'SKU-CLTH-002', @cat_cloth,  '件',   599.00, 1),
(11, '阿迪达斯跑步裤',             'SKU-CLTH-003', @cat_cloth,  '条',   279.00, 1),
(12, '耐克Air Max 270运动鞋',      'SKU-CLTH-004', @cat_cloth,  '双',   899.00, 1),
(13, '旺旺雪饼大袋装',             'SKU-FOOD-001', @cat_food,   '袋',    12.90, 1),
(14, '旺旺仙贝袋装',               'SKU-FOOD-002', @cat_food,   '袋',     9.90, 1),
(15, '农夫山泉550ml整箱24瓶',      'SKU-FOOD-003', @cat_food,   '箱',    36.00, 1),
(16, '统一老坛酸菜牛肉面整箱',     'SKU-FOOD-004', @cat_food,   '箱',    68.00, 1),
(17, '得力12号订书机',             'SKU-OFCE-001', @cat_office, '个',    28.00, 1),
(18, '晨光黑色签字笔盒装10支',     'SKU-OFCE-002', @cat_office, '盒',    18.00, 1),
(19, '惠普A4复印纸80g整箱5包装',   'SKU-OFCE-003', @cat_office, '箱',   135.00, 1),
(20, '惠普HP64原装墨盒套装',       'SKU-OFCE-004', @cat_office, '套',   259.00, 1)
ON DUPLICATE KEY UPDATE id=id;

-- =============================================
-- 追加：库存（合计 3179 件，4款低于预警）
-- 预警品：MacBook Air M3 / ThinkPad X1 / Sony WH-1000XM5 / HP墨盒
-- =============================================
INSERT INTO inventory (warehouse_id, product_id, qty, alert_qty) VALUES
(1,  1,  50,  20),
(1,  2,  42,  15),
(1,  3,  80,  20),
(1,  4,  35,  40),
(1,  5,  18,  20),
(1,  6,  60,  15),
(1,  7,  95, 100),
(1,  8, 180,  50),
(1,  9, 400,  30),
(1, 10, 120,  20),
(1, 11, 145,  30),
(1, 12,  75,  20),
(1, 13, 530, 100),
(1, 14, 420, 100),
(1, 15, 280,  50),
(1, 16, 230,  50),
(1, 17,  80,  20),
(1, 18, 150,  30),
(1, 19, 120,  20),
(1, 20,  69,  70)
ON DUPLICATE KEY UPDATE qty=VALUES(qty), alert_qty=VALUES(alert_qty);

-- =============================================
-- Schema migrations（幂等，每次启动自动执行）
-- =============================================
ALTER TABLE out_order_item ADD COLUMN IF NOT EXISTS actual_qty INT NOT NULL DEFAULT 0;
ALTER TABLE inventory ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;

-- =============================================
-- 性能索引（幂等，每次启动自动执行）
-- =============================================
-- 高频：confirm/delete 按 order_id 查明细行
CREATE INDEX IF NOT EXISTS idx_in_order_item_order  ON in_order_item(order_id);
CREATE INDEX IF NOT EXISTS idx_out_order_item_order ON out_order_item(order_id);
-- 高频：删除已确认订单时反查库存日志
CREATE INDEX IF NOT EXISTS idx_inv_log_ref_order    ON inventory_log(ref_order_id);
-- 中频：订单列表按状态+仓库筛选
CREATE INDEX IF NOT EXISTS idx_in_order_status_wh   ON in_order(status, warehouse_id);
CREATE INDEX IF NOT EXISTS idx_out_order_status_wh  ON out_order(status, warehouse_id);