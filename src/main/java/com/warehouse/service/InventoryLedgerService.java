package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.StockSnapshot;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public interface InventoryLedgerService {

    Page<InventoryLedger> page(int current, int size, Long productId, Long locationId,
                               String type, String startDate, String endDate);

    List<StockSnapshot> rebuildSnapshot();

    void exportLedger(Long productId, Long locationId, String type,
                      String startDate, String endDate, HttpServletResponse response) throws IOException;
}