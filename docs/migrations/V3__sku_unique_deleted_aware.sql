-- V3__sku_unique_deleted_aware.sql
-- 目的：让 product.sku_code 的唯一约束"只对活动商品(deleted=0)生效"。
-- 背景：删除商品为软删除(deleted=1，行保留)，而旧的全局 UNIQUE(sku_code) 会让软删行
--       继续占用 SKU，复用旧 SKU 新增时报 Duplicate entry。
-- 方案：派生列 sku_active（活动行=sku_code，软删行=NULL，MySQL 不对 NULL 做唯一校验）。
-- 说明：生产环境由 LedgerMigrationRunner.ensureSkuDeletedAwareUnique() 在启动时幂等执行；
--       本文件用于留档与手动执行场景。要求 MySQL 5.7+（已确认生产为 8.0.46）。
-- 前置校验：活动商品不得已有重复 SKU，否则新唯一索引建立会失败：
--   SELECT sku_code, COUNT(*) FROM product WHERE deleted=0 GROUP BY sku_code HAVING COUNT(*)>1;

ALTER TABLE product
  ADD COLUMN sku_active VARCHAR(100)
  GENERATED ALWAYS AS (IF(deleted = 0, sku_code, NULL)) STORED;

ALTER TABLE product ADD UNIQUE KEY uq_sku_active (sku_active);

-- 删除旧的全局单列唯一索引（列级 UNIQUE 默认索引名为 sku_code）
ALTER TABLE product DROP INDEX sku_code;

-- ============ 回滚（如需还原）============
-- 注意：仅当不存在"活动+软删 SKU 相同"的组合时才可安全恢复全局唯一索引。
-- ALTER TABLE product DROP INDEX uq_sku_active;
-- ALTER TABLE product ADD UNIQUE KEY sku_code (sku_code);
-- ALTER TABLE product DROP COLUMN sku_active;
