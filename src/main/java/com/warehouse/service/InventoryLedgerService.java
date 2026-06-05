package com.warehouse.service;

import com.warehouse.entity.StockSnapshot;
import java.util.List;

public interface InventoryLedgerService {

    /**
     * 从 inventory_ledger 流水重算所有 stock_snapshot，
     * 并同步 alert_qty。账实不符时的复位手段。
     * @return 重算后的全部快照（可用于人工校验）
     */
    List<StockSnapshot> rebuildSnapshot();
}
