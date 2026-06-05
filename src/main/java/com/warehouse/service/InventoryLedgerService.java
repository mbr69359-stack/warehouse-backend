package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.StockSnapshot;
import java.util.List;

public interface InventoryLedgerService {

    Page<InventoryLedger> page(int current, int size, Long productId, Long locationId, String type);

    List<StockSnapshot> rebuildSnapshot();
}
