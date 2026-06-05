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
        migrateOpeningBalances();
        rebuildSnapshot();
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
        createIndexIfMissing("inventory_ledger", "idx_ledger_prod_loc_time",
            "CREATE INDEX idx_ledger_prod_loc_time ON inventory_ledger(product_id, location_id, occurred_at)");
        createIndexIfMissing("inventory_ledger", "idx_ledger_document_no",
            "CREATE INDEX idx_ledger_document_no ON inventory_ledger(document_no)");
        createIndexIfMissing("inventory_ledger", "idx_ledger_type",
            "CREATE INDEX idx_ledger_type ON inventory_ledger(type, occurred_at)");

        log.info("[LedgerMigration] 表结构检查完毕");
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

    private void migrateOpeningBalances() {
        // 只对尚未有 opening 流水的 product+location 插入期初记录
        int inserted = jdbc.update(
            "INSERT INTO inventory_ledger (id, product_id, location_id, change_qty, type, operator, note, occurred_at) " +
            "SELECT UUID(), i.product_id, i.warehouse_id, i.qty, 'opening', 'system', '系统迁移期初库存', UTC_TIMESTAMP() " +
            "FROM inventory i " +
            "WHERE i.qty != 0 " +
            "  AND NOT EXISTS (" +
            "      SELECT 1 FROM inventory_ledger l " +
            "      WHERE l.product_id = i.product_id AND l.location_id = i.warehouse_id AND l.type = 'opening'" +
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