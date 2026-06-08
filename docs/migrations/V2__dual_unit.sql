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