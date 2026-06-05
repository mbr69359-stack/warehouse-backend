package com.warehouse.service.impl;

import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.service.InventoryLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryLedgerServiceImpl implements InventoryLedgerService {

    private final StockSnapshotMapper stockSnapshotMapper;

    @Override
    @Transactional
    public List<StockSnapshot> rebuildSnapshot() {
        stockSnapshotMapper.rebuildAllFromLedger();
        stockSnapshotMapper.syncAlertQtyFromInventory();
        return stockSnapshotMapper.selectAll();
    }
}
