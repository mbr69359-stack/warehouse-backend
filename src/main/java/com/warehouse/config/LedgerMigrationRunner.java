package com.warehouse.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时自动检测并执行 inventory_ledger / stock_snapshot 迁移。
 * 幂等：已迁移过则只做校验打印，不会重复插入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        log.info("[LedgerMigration] 开始检查 append-only 流水账迁移状态...");

        ensureTables();
        ensureProductUuid();
        ensureProductSpecBarcode();
        ensureDamageTables();
        ensureOutOrderExchangeNo();
        ensureReturnInboundColumns();
        migrateOpeningBalances();
        rebuildSnapshot();
        syncInventoryFromLedger();
        printVerification();

        log.info("[LedgerMigration] 迁移检查完成");
    }

    private void ensureTables() {
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS inventory_ledger (" +
            "  id          CHAR(36)      NOT NULL PRIMARY KEY," +
            "  product_id  BIGINT        NOT NULL," +
            "  location_id BIGINT        NOT NULL DEFAULT 0," +
            "  change_qty  DECIMAL(14,3) NOT NULL," +
            "  type        VARCHAR(20)   NOT NULL," +
            "  document_no VARCHAR(50)   NULL," +
            "  operator    VARCHAR(100)  NOT NULL DEFAULT ''," +
            "  note        VARCHAR(500)  NULL," +
            "  occurred_at DATETIME      NOT NULL," +
            "  device_id   VARCHAR(100)  NULL," +
            "  synced      TINYINT       NOT NULL DEFAULT 1," +
            "  created_at  DATETIME      NOT NULL DEFAULT (UTC_TIMESTAMP())" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS stock_snapshot (" +
            "  product_id  BIGINT        NOT NULL," +
            "  location_id BIGINT        NOT NULL DEFAULT 0," +
            "  current_qty DECIMAL(14,3) NOT NULL DEFAULT 0," +
            "  alert_qty   INT           NOT NULL DEFAULT 0," +
            "  updated_at  DATETIME      NOT NULL," +
            "  PRIMARY KEY (product_id, location_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS sync_processed_log (" +
            "  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY," +
            "  client_id     VARCHAR(128) NOT NULL," +
            "  local_id      BIGINT       NOT NULL," +
            "  success       TINYINT      NOT NULL," +
            "  reject_reason VARCHAR(500) NULL," +
            "  created_at    DATETIME     NOT NULL DEFAULT (UTC_TIMESTAMP())," +
            "  processed_at  DATETIME     NOT NULL DEFAULT (UTC_TIMESTAMP())," +
            "  UNIQUE KEY uq_sync_processed_client_local (client_id, local_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        ensureSyncProcessedLogSchema();
        createIndexIfMissing("inventory_ledger", "idx_ledger_prod_loc_time",
            "CREATE INDEX idx_ledger_prod_loc_time ON inventory_ledger(product_id, location_id, occurred_at)");
        createIndexIfMissing("inventory_ledger", "idx_ledger_document_no",
            "CREATE INDEX idx_ledger_document_no ON inventory_ledger(document_no)");
        createIndexIfMissing("inventory_ledger", "idx_ledger_type",
            "CREATE INDEX idx_ledger_type ON inventory_ledger(type, occurred_at)");

        log.info("[LedgerMigration] 表结构检查完毕");
    }

    private void ensureSyncProcessedLogSchema() {
        addColumnIfMissing("sync_processed_log", "client_id",
            "ALTER TABLE sync_processed_log ADD COLUMN client_id VARCHAR(128) NULL");
        addColumnIfMissing("sync_processed_log", "local_id",
            "ALTER TABLE sync_processed_log ADD COLUMN local_id BIGINT NULL");
        // 旧 schema 升级遗留的 client_id 为 NULL/空 的行属于旧幂等方案的废弃记录，直接清理，
        // 避免下方 MODIFY COLUMN client_id NOT NULL 因存量 NULL 值导致启动失败
        jdbc.update("DELETE FROM sync_processed_log WHERE client_id IS NULL OR client_id = ''");
        if (columnExists("sync_processed_log", "uuid")) {
            jdbc.execute("ALTER TABLE sync_processed_log MODIFY COLUMN uuid VARCHAR(64) NULL");
        }

        addColumnIfMissing("sync_processed_log", "id",
            "ALTER TABLE sync_processed_log ADD COLUMN id BIGINT NULL");

        Integer nullIds = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sync_processed_log WHERE id IS NULL",
            Integer.class);
        if (nullIds != null && nullIds > 0) {
            Long maxId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM sync_processed_log", Long.class);
            jdbc.execute("SET @sync_processed_log_rownum := " + (maxId == null ? 0 : maxId));
            jdbc.update(
                "UPDATE sync_processed_log " +
                "SET id = (@sync_processed_log_rownum := @sync_processed_log_rownum + 1) " +
                "WHERE id IS NULL " +
                "ORDER BY created_at, local_id");
        }

        List<String> primaryColumns = jdbc.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE " +
            "WHERE TABLE_SCHEMA = DATABASE() " +
            "  AND TABLE_NAME = 'sync_processed_log' " +
            "  AND CONSTRAINT_NAME = 'PRIMARY' " +
            "ORDER BY ORDINAL_POSITION",
            String.class);
        boolean idIsPrimary = primaryColumns.size() == 1 && "id".equalsIgnoreCase(primaryColumns.get(0));
        if (!idIsPrimary) {
            if (!primaryColumns.isEmpty()) {
                jdbc.execute("ALTER TABLE sync_processed_log DROP PRIMARY KEY");
            }
            jdbc.execute("ALTER TABLE sync_processed_log MODIFY COLUMN id BIGINT NOT NULL");
            jdbc.execute("ALTER TABLE sync_processed_log ADD PRIMARY KEY (id)");
        }
        jdbc.execute("ALTER TABLE sync_processed_log MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT");
        jdbc.execute("ALTER TABLE sync_processed_log MODIFY COLUMN client_id VARCHAR(128) NOT NULL");

        jdbc.update(
            "DELETE newer FROM sync_processed_log newer " +
            "JOIN sync_processed_log older " +
            "  ON newer.client_id = older.client_id " +
            " AND newer.local_id = older.local_id " +
            " AND newer.id > older.id " +
            "WHERE newer.local_id IS NOT NULL");
        dropSingleColumnLocalIdUniqueIndexes();
        createIndexIfMissing("sync_processed_log", "uq_sync_processed_client_local",
            "ALTER TABLE sync_processed_log ADD UNIQUE KEY uq_sync_processed_client_local (client_id, local_id)");
    }

    private void dropSingleColumnLocalIdUniqueIndexes() {
        List<String> indexes = jdbc.queryForList(
            "SELECT INDEX_NAME " +
            "FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA = DATABASE() " +
            "  AND TABLE_NAME = 'sync_processed_log' " +
            "  AND NON_UNIQUE = 0 " +
            "  AND INDEX_NAME <> 'PRIMARY' " +
            "GROUP BY INDEX_NAME " +
            "HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME = 'local_id') = 1",
            String.class);
        for (String index : indexes) {
            jdbc.execute("ALTER TABLE sync_processed_log DROP INDEX `" + index.replace("`", "``") + "`");
        }
    }

    private boolean columnExists(String table, String column) {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
            Integer.class, table, column);
        return cnt != null && cnt > 0;
    }

    private void ensureProductUuid() {
        int colExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'uuid'",
            Integer.class);
        if (colExists == 0) {
            jdbc.execute("ALTER TABLE product ADD COLUMN uuid CHAR(36) NOT NULL DEFAULT ''");
            log.info("[LedgerMigration] product.uuid 列已添加");
        }
        jdbc.execute("UPDATE product SET uuid = UUID() WHERE uuid = '' OR uuid IS NULL");

        int idxExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND INDEX_NAME = 'uq_product_uuid'",
            Integer.class);
        if (idxExists == 0) {
            jdbc.execute("ALTER TABLE product ADD UNIQUE KEY uq_product_uuid (uuid)");
        }
    }

    private void ensureProductSpecBarcode() {
        addColumnIfMissing("product", "spec", "ALTER TABLE product ADD COLUMN spec VARCHAR(500) NULL");
        addColumnIfMissing("product", "barcode", "ALTER TABLE product ADD COLUMN barcode VARCHAR(100) NULL");
    }

    private void ensureDamageTables() {
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS damage_record (" +
            "  id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY," +
            "  warehouse_id BIGINT        NOT NULL," +
            "  product_id   BIGINT        NOT NULL," +
            "  qty          INT           NOT NULL," +
            "  status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING'," +
            "  remark       VARCHAR(500)  NULL," +
            "  created_at   DATETIME      NOT NULL DEFAULT (UTC_TIMESTAMP())," +
            "  created_by   VARCHAR(100)  NOT NULL DEFAULT ''," +
            "  resolved_at  DATETIME      NULL," +
            "  out_order_id BIGINT        NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS customer_return (" +
            "  id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY," +
            "  exchange_no  VARCHAR(50)   NOT NULL," +
            "  warehouse_id BIGINT        NOT NULL," +
            "  status       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'," +
            "  remark       VARCHAR(500)  NULL," +
            "  created_at   DATETIME      NOT NULL DEFAULT (UTC_TIMESTAMP())," +
            "  created_by   VARCHAR(100)  NOT NULL DEFAULT ''," +
            "  out_order_id BIGINT        NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS customer_return_item (" +
            "  id        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
            "  return_id BIGINT NOT NULL," +
            "  product_id BIGINT NOT NULL," +
            "  qty       INT    NOT NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        log.info("[LedgerMigration] 损坏/退换货表检查完毕");
    }

    private void ensureOutOrderExchangeNo() {
        addColumnIfMissing("out_order", "exchange_no",
            "ALTER TABLE out_order ADD COLUMN exchange_no VARCHAR(50) NULL");
    }

    private void ensureReturnInboundColumns() {
        Integer hasInOrderId = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customer_return' AND COLUMN_NAME = 'in_order_id'",
            Integer.class);
        if (hasInOrderId != null && hasInOrderId == 0) {
            // 首次迁移：清理旧流程产生的草稿单（库存未变动，可安全删除）
            jdbc.update(
                "DELETE oo FROM out_order oo " +
                "JOIN customer_return cr ON cr.out_order_id = oo.id " +
                "WHERE cr.status = 'DRAFT'");
            jdbc.update("DELETE FROM customer_return WHERE status = 'DRAFT'");
            jdbc.execute("ALTER TABLE customer_return ADD COLUMN in_order_id BIGINT NULL");
            log.info("[LedgerMigration] customer_return.in_order_id 已添加，旧草稿数据已清理");
        }
        addColumnIfMissing("damage_record", "source",
            "ALTER TABLE damage_record ADD COLUMN source VARCHAR(30) NULL");
        addColumnIfMissing("damage_record", "source_id",
            "ALTER TABLE damage_record ADD COLUMN source_id BIGINT NULL");
    }

    private void addColumnIfMissing(String table, String column, String alterSql) {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
            Integer.class, table, column);
        if (cnt != null && cnt == 0) {
            jdbc.execute(alterSql);
            log.info("[LedgerMigration] {}.{} 列已添加", table, column);
        }
    }

    private void migrateOpeningBalances() {
        // 只对尚未有 opening 流水的 product+location 插入期初记录
        int inserted = jdbc.update(
            "INSERT INTO inventory_ledger (id, product_id, location_id, change_qty, type, operator, note, occurred_at) " +
            "SELECT UUID(), i.product_id, i.warehouse_id, i.qty, 'opening', 'system', '系统迁移期初库存', UTC_TIMESTAMP() " +
            "FROM inventory i " +
            "WHERE i.qty != 0 " +
            "  AND NOT EXISTS (" +
            "      SELECT 1 FROM inventory_ledger l " +
            "      WHERE l.product_id = i.product_id AND l.location_id = i.warehouse_id" +
            "  )"
        );
        if (inserted > 0) {
            log.info("[LedgerMigration] 写入期初流水 {} 条", inserted);
        } else {
            log.info("[LedgerMigration] 期初流水已存在，跳过写入");
        }
    }

    private void rebuildSnapshot() {
        jdbc.update(
            "INSERT INTO stock_snapshot (product_id, location_id, current_qty, alert_qty, updated_at) " +
            "SELECT l.product_id, l.location_id, SUM(l.change_qty), IFNULL(i.alert_qty, 0), UTC_TIMESTAMP() " +
            "FROM inventory_ledger l " +
            "LEFT JOIN inventory i ON i.product_id = l.product_id AND i.warehouse_id = l.location_id " +
            "GROUP BY l.product_id, l.location_id " +
            "ON DUPLICATE KEY UPDATE " +
            "  current_qty = VALUES(current_qty), alert_qty = VALUES(alert_qty), updated_at = UTC_TIMESTAMP()"
        );
        log.info("[LedgerMigration] stock_snapshot 已更新");
    }

    private void syncInventoryFromLedger() {
        int updated = jdbc.update(
            "UPDATE inventory i " +
            "JOIN (SELECT product_id, location_id, SUM(change_qty) AS ledger_sum " +
            "      FROM inventory_ledger GROUP BY product_id, location_id) l " +
            "  ON l.product_id = i.product_id AND l.location_id = i.warehouse_id " +
            "SET i.qty = GREATEST(0, l.ledger_sum) " +
            "WHERE i.qty != GREATEST(0, l.ledger_sum)"
        );
        if (updated > 0) {
            log.info("[LedgerMigration] inventory.qty 漂移修正 {} 条", updated);
        }
    }

    private void printVerification() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.name AS product_name, p.sku_code, w.name AS warehouse_name, " +
            "       i.qty AS legacy_qty, s.current_qty AS snapshot_qty, " +
            "       IF(i.qty = s.current_qty, 'OK', CONCAT('DIFF=', i.qty - s.current_qty)) AS result " +
            "FROM inventory i " +
            "JOIN product p ON p.id = i.product_id " +
            "JOIN warehouse w ON w.id = i.warehouse_id " +
            "LEFT JOIN stock_snapshot s ON s.product_id = i.product_id AND s.location_id = i.warehouse_id " +
            "ORDER BY result DESC, p.name"
        );

        long ok   = rows.stream().filter(r -> "OK".equals(r.get("result"))).count();
        long diff = rows.size() - ok;

        log.info("[LedgerMigration] ====== 库存迁移校验报告 ======");
        log.info("[LedgerMigration] 总记录: {}  一致: {}  不一致: {}", rows.size(), ok, diff);
        for (Map<String, Object> row : rows) {
            String status = "OK".equals(row.get("result")) ? "✓" : "✗";
            log.info("[LedgerMigration] {} {} ({}) @ {} | legacy={} snapshot={}",
                status,
                row.get("product_name"), row.get("sku_code"),
                row.get("warehouse_name"),
                row.get("legacy_qty"), row.get("snapshot_qty"));
        }
        log.info("[LedgerMigration] ================================");

        if (diff > 0) {
            log.error("[LedgerMigration] ⚠ 发现 {} 条不一致记录！请检查日志并手动修正", diff);
        }
    }

    private void createIndexIfMissing(String table, String indexName, String createSql) {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
            Integer.class, table, indexName);
        if (cnt != null && cnt == 0) {
            jdbc.execute(createSql);
        }
    }
}
