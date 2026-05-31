CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGSERIAL PRIMARY KEY,
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
    id          BIGSERIAL PRIMARY KEY,
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
    id          BIGSERIAL PRIMARY KEY,
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
    id          BIGSERIAL PRIMARY KEY,
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
    id        BIGSERIAL PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name      VARCHAR(100) NOT NULL,
    sort      INT NOT NULL DEFAULT 0,
    deleted   SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS product (
    id          BIGSERIAL PRIMARY KEY,
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
    id          BIGSERIAL PRIMARY KEY,
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
    id           BIGSERIAL PRIMARY KEY,
    order_no     VARCHAR(30)  NOT NULL UNIQUE,
    warehouse_id BIGINT NOT NULL,
    supplier_id  BIGINT,
    type         VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    operator_id  BIGINT NOT NULL,
    remark       VARCHAR(500),
    deleted      SMALLINT NOT NULL DEFAULT 0,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS in_order_item (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    plan_qty   INT NOT NULL DEFAULT 0,
    actual_qty INT NOT NULL DEFAULT 0,
    price      DECIMAL(12,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE IF NOT EXISTS out_order (
    id           BIGSERIAL PRIMARY KEY,
    order_no     VARCHAR(30)  NOT NULL UNIQUE,
    warehouse_id BIGINT NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    operator_id  BIGINT NOT NULL,
    remark       VARCHAR(500),
    deleted      SMALLINT NOT NULL DEFAULT 0,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS out_order_item (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    qty        INT NOT NULL DEFAULT 0,
    price      DECIMAL(12,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE IF NOT EXISTS inventory (
    id           BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    product_id   BIGINT NOT NULL,
    qty          INT NOT NULL DEFAULT 0,
    alert_qty    INT NOT NULL DEFAULT 0,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (warehouse_id, product_id)
);

CREATE TABLE IF NOT EXISTS inventory_log (
    id           BIGSERIAL PRIMARY KEY,
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
ON CONFLICT (role_code) DO NOTHING;

-- 默认管理员 密码: admin123
INSERT INTO sys_user (id, username, password, real_name, status) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyU8XIVWK', '系统管理员', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1)
ON CONFLICT DO NOTHING;

INSERT INTO warehouse (id, name, address, manager_id, status) VALUES
(1, '主仓库', '默认地址', 1, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO category (parent_id, name, sort)
SELECT 0, '电子产品', 1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='电子产品' AND parent_id=0 AND deleted=0);
INSERT INTO category (parent_id, name, sort)
SELECT 0, '服装', 2 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='服装' AND parent_id=0 AND deleted=0);
INSERT INTO category (parent_id, name, sort)
SELECT 0, '食品', 3 WHERE NOT EXISTS (SELECT 1 FROM category WHERE name='食品' AND parent_id=0 AND deleted=0);
