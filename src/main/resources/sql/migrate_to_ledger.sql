-- =============================================================
-- migrate_to_ledger.sql
-- 一次性在生产数据库执行，将现有库存迁移到 append-only 流水账
-- 执行前请确认已备份数据库
-- =============================================================

-- Step 1: 建新表（幂等，与 init.sql 一致）
ALTER TABLE product ADD COLUMN IF NOT EXISTS uuid CHAR(36) NOT NULL DEFAULT '';
UPDATE product SET uuid = UUID() WHERE uuid = '';

-- 补全可能遗漏的唯一索引
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product'
      AND INDEX_NAME = 'uq_product_uuid'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE product ADD UNIQUE KEY uq_product_uuid (uuid)',
    'SELECT ''uq_product_uuid already exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS inventory_ledger (
    id          CHAR(36)       NOT NULL PRIMARY KEY,
    product_id  BIGINT         NOT NULL,
    location_id BIGINT         NOT NULL DEFAULT 0,
    change_qty  DECIMAL(14,3)  NOT NULL,
    type        VARCHAR(20)    NOT NULL,
    document_no VARCHAR(50)    NULL,
    operator    VARCHAR(100)   NOT NULL DEFAULT '',
    note        VARCHAR(500)   NULL,
    occurred_at DATETIME       NOT NULL,
    device_id   VARCHAR(100)   NULL,
    synced      TINYINT        NOT NULL DEFAULT 1,
    created_at  DATETIME       NOT NULL DEFAULT (UTC_TIMESTAMP())
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX IF NOT EXISTS idx_ledger_prod_loc_time ON inventory_ledger(product_id, location_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_ledger_document_no   ON inventory_ledger(document_no);
CREATE INDEX IF NOT EXISTS idx_ledger_type          ON inventory_ledger(type, occurred_at);

CREATE TABLE IF NOT EXISTS stock_snapshot (
    product_id  BIGINT         NOT NULL,
    location_id BIGINT         NOT NULL DEFAULT 0,
    current_qty DECIMAL(14,3)  NOT NULL DEFAULT 0,
    alert_qty   INT            NOT NULL DEFAULT 0,
    updated_at  DATETIME       NOT NULL,
    PRIMARY KEY (product_id, location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Step 2: 写入期初流水（幂等：用 product_id+location_id 防止重复）
-- 只对尚未有任何流水的商品+仓库组合生成期初记录
INSERT INTO inventory_ledger (id, product_id, location_id, change_qty, type, operator, note, occurred_at)
SELECT
    UUID(),
    i.product_id,
    i.warehouse_id,
    i.qty,
    'opening',
    'system',
    '系统迁移期初库存',
    UTC_TIMESTAMP()
FROM inventory i
WHERE i.qty != 0
  AND NOT EXISTS (
      SELECT 1 FROM inventory_ledger l
      WHERE l.product_id  = i.product_id
        AND l.location_id = i.warehouse_id
        AND l.type = 'opening'
  );

-- Step 3: 生成 stock_snapshot（从流水重算）
INSERT INTO stock_snapshot (product_id, location_id, current_qty, alert_qty, updated_at)
SELECT
    l.product_id,
    l.location_id,
    SUM(l.change_qty),
    IFNULL(i.alert_qty, 0),
    UTC_TIMESTAMP()
FROM inventory_ledger l
LEFT JOIN inventory i ON i.product_id = l.product_id AND i.warehouse_id = l.location_id
GROUP BY l.product_id, l.location_id
ON DUPLICATE KEY UPDATE
    current_qty = VALUES(current_qty),
    alert_qty   = VALUES(alert_qty),
    updated_at  = UTC_TIMESTAMP();

-- Step 4: 校验报告（迁移前 qty vs 迁移后 snapshot current_qty）
SELECT
    p.name                                                              AS 商品名称,
    p.sku_code                                                          AS SKU,
    w.name                                                              AS 仓库,
    i.qty                                                               AS 迁移前qty,
    s.current_qty                                                       AS 迁移后snapshot,
    IF(i.qty = s.current_qty, '✓ 一致', CONCAT('✗ 差值=', i.qty - s.current_qty)) AS 校验结果
FROM inventory i
JOIN product p       ON p.id = i.product_id
JOIN warehouse w     ON w.id = i.warehouse_id
LEFT JOIN stock_snapshot s ON s.product_id = i.product_id AND s.location_id = i.warehouse_id
ORDER BY 校验结果 DESC, p.name;

-- Step 5: 汇总行
SELECT
    COUNT(*)                                                            AS 总记录数,
    SUM(IF(i.qty = s.current_qty, 1, 0))                              AS 一致数量,
    SUM(IF(i.qty != s.current_qty OR s.current_qty IS NULL, 1, 0))    AS 不一致数量
FROM inventory i
LEFT JOIN stock_snapshot s ON s.product_id = i.product_id AND s.location_id = i.warehouse_id;