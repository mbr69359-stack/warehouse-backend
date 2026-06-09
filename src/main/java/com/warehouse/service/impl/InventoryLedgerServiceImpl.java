package com.warehouse.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.service.InventoryLedgerService;
import com.warehouse.vo.LedgerExportRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryLedgerServiceImpl implements InventoryLedgerService {

    private static final Map<String, String> TYPE_NAME_MAP = new HashMap<>();
    static {
        TYPE_NAME_MAP.put("inbound",          "入库");
        TYPE_NAME_MAP.put("outbound",         "出库");
        TYPE_NAME_MAP.put("adjust",           "调整");
        TYPE_NAME_MAP.put("transfer",         "调拨出");
        TYPE_NAME_MAP.put("transfer_out",     "调拨出");
        TYPE_NAME_MAP.put("transfer_in",      "调拨入");
        TYPE_NAME_MAP.put("opening",          "期初");
        TYPE_NAME_MAP.put("inbound_cancel",   "入库撤销");
        TYPE_NAME_MAP.put("outbound_cancel",  "出库撤销");
        TYPE_NAME_MAP.put("transfer_cancel",  "调拨撤销");
        TYPE_NAME_MAP.put("damage",           "破损扣减");
        TYPE_NAME_MAP.put("damage_out",       "损坏出库");
        TYPE_NAME_MAP.put("damage_cancel",    "损坏撤销");
        TYPE_NAME_MAP.put("replacement_out",  "补发出库");
    }

    private final InventoryLedgerMapper ledgerMapper;
    private final StockSnapshotMapper stockSnapshotMapper;
    private final ProductMapper productMapper;

    @Override
    public Page<InventoryLedger> page(int current, int size, Long productId, Long locationId,
                                      String type, String startDate, String endDate) {
        LocalDateTime start = startDate != null ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end   = endDate   != null ? LocalDate.parse(endDate).atTime(23, 59, 59) : null;
        LambdaQueryWrapper<InventoryLedger> q = new LambdaQueryWrapper<InventoryLedger>()
                .eq(productId  != null, InventoryLedger::getProductId,  productId)
                .eq(locationId != null, InventoryLedger::getLocationId, locationId)
                .eq(type       != null, InventoryLedger::getType,       type)
                .ge(start      != null, InventoryLedger::getOccurredAt, start)
                .le(end        != null, InventoryLedger::getOccurredAt, end)
                .orderByDesc(InventoryLedger::getOccurredAt);
        Page<InventoryLedger> pageResult = ledgerMapper.selectPage(new Page<>(current, size), q);

        List<InventoryLedger> records = pageResult.getRecords();
        if (!records.isEmpty()) {
            Set<Long> productIds = records.stream()
                    .map(InventoryLedger::getProductId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            Map<Long, String> nameMap = productMapper.selectBatchIds(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, Product::getName));
            records.forEach(r -> r.setProductName(nameMap.get(r.getProductId())));
        }
        return pageResult;
    }

    @Override
    @Transactional
    public List<StockSnapshot> rebuildSnapshot() {
        stockSnapshotMapper.rebuildAllFromLedger();
        stockSnapshotMapper.syncAlertQtyFromInventory();
        return stockSnapshotMapper.selectAll();
    }

    @Override
    public void exportLedger(Long productId, Long locationId, String type,
                             String startDate, String endDate, HttpServletResponse response) throws IOException {
        List<LedgerExportRow> rows = ledgerMapper.selectForExport(productId, locationId, type, startDate, endDate);
        // 将原始类型代码转为中文
        rows.forEach(r -> r.setTypeName(TYPE_NAME_MAP.getOrDefault(r.getTypeName(), r.getTypeName())));

        String fileName = URLEncoder.encode("库存台账_" + LocalDate.now(), "UTF-8").replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), LedgerExportRow.class)
                .sheet("库存台账")
                .doWrite(rows);
    }
}