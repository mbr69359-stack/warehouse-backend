package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.service.InventoryLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryLedgerServiceImpl implements InventoryLedgerService {

    private final InventoryLedgerMapper ledgerMapper;
    private final StockSnapshotMapper stockSnapshotMapper;
    private final ProductMapper productMapper;

    @Override
    public Page<InventoryLedger> page(int current, int size, Long productId, Long locationId, String type) {
        LambdaQueryWrapper<InventoryLedger> q = new LambdaQueryWrapper<InventoryLedger>()
                .eq(productId != null, InventoryLedger::getProductId, productId)
                .eq(locationId != null, InventoryLedger::getLocationId, locationId)
                .eq(type != null, InventoryLedger::getType, type)
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
}
