package com.warehouse.service.impl;

import com.warehouse.common.BusinessException;
import com.warehouse.dto.SyncItemDTO;
import com.warehouse.dto.SyncResultDTO;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private final InventoryLedgerMapper inventoryLedgerMapper;
    private final StockSnapshotMapper stockSnapshotMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SyncResultDTO> batchSync(List<SyncItemDTO> items) {
        items.sort(Comparator.comparing(SyncItemDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // Phase 1: 全批校验（从 snapshot 读当前库存，累计模拟，不写 DB）
        Map<String, BigDecimal> simQty = new HashMap<>();
        List<SyncResultDTO> phase1 = new ArrayList<>();
        boolean hasError = false;
        for (int i = 0; i < items.size(); i++) {
            SyncItemDTO item = items.get(i);
            String key = item.getWarehouseId() + ":" + item.getProductId();
            if (!simQty.containsKey(key)) {
                StockSnapshot snap = stockSnapshotMapper.selectOne(item.getProductId(), item.getWarehouseId());
                simQty.put(key, snap != null ? snap.getCurrentQty() : BigDecimal.ZERO);
            }
            int absQty = Math.abs(item.getQty());
            boolean isOut = "OUT".equals(item.getType()) || item.getQty() < 0;
            BigDecimal current = simQty.get(key);
            if (isOut && current.compareTo(BigDecimal.valueOf(absQty)) < 0) {
                phase1.add(SyncResultDTO.fail(i,
                        "库存不足，批次模拟库存：" + current.toPlainString() + "，需要：" + absQty));
                hasError = true;
            } else {
                phase1.add(null);
                BigDecimal delta = BigDecimal.valueOf(isOut ? -absQty : absQty);
                simQty.put(key, current.add(delta));
            }
        }
        if (hasError) {
            for (int i = 0; i < phase1.size(); i++) {
                if (phase1.get(i) == null) {
                    phase1.set(i, SyncResultDTO.fail(i, "批次内存在库存不足，整批已拒绝"));
                }
            }
            return phase1;
        }

        // Phase 2: 全批执行 — 只追加流水 + 更新 snapshot，不直接修改 inventory
        List<SyncResultDTO> results = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SyncItemDTO item = items.get(i);
            int absQty = Math.abs(item.getQty());
            boolean isOut = "OUT".equals(item.getType()) || item.getQty() < 0;
            int delta = isOut ? -absQty : absQty;

            StockSnapshot snap = stockSnapshotMapper.selectOne(item.getProductId(), item.getWarehouseId());
            BigDecimal beforeQty = snap != null ? snap.getCurrentQty() : BigDecimal.ZERO;

            if (isOut && beforeQty.compareTo(BigDecimal.valueOf(absQty)) < 0) {
                throw new BusinessException("库存在校验期间被并发修改，请稍后重试");
            }

            // 追加流水（核心：只增不改）
            InventoryLedger ledger = new InventoryLedger();
            ledger.setId(UUID.randomUUID().toString());
            ledger.setProductId(item.getProductId());
            ledger.setLocationId(item.getWarehouseId());
            ledger.setChangeQty(BigDecimal.valueOf(delta));
            ledger.setType(isOut ? "outbound" : "inbound");
            ledger.setOperator("");
            ledger.setNote(item.getRemark());
            ledger.setOccurredAt(item.getCreatedAt() != null ? item.getCreatedAt() : LocalDateTime.now(ZoneOffset.UTC));
            ledger.setSynced(1);
            ledger.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            inventoryLedgerMapper.insert(ledger);

            // 更新快照缓存
            BigDecimal newQty = beforeQty.add(BigDecimal.valueOf(delta));
            stockSnapshotMapper.upsert(
                item.getProductId(), item.getWarehouseId(),
                newQty,
                snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0
            );

            results.add(SyncResultDTO.ok(i));
        }
        return results;
    }
}