-- =============================================================
-- migrate_product_weight.sql
-- 在生产数据库执行一次，添加商品箱规字段
-- =============================================================

SET @w = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'weight_per_box');
SET @sql = IF(@w = 0,
    'ALTER TABLE product ADD COLUMN weight_per_box DECIMAL(10,2) DEFAULT NULL COMMENT ''每箱重量(kg)''',
    'SELECT ''weight_per_box already exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @q = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'qty_per_box');
SET @sql2 = IF(@q = 0,
    'ALTER TABLE product ADD COLUMN qty_per_box INT DEFAULT NULL COMMENT ''每箱片数''',
    'SELECT ''qty_per_box already exists'' AS info');
PREPARE stmt FROM @sql2; EXECUTE stmt; DEALLOCATE PREPARE stmt;