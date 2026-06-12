package com.warehouse.service.impl;

import com.warehouse.common.BusinessException;
import com.warehouse.dto.SyncItemDTO;
import com.warehouse.dto.SyncResultDTO;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.entity.SyncProcessedLog;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.mapper.SyncProcessedLogMapper;
import com.warehouse.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    private final SyncProcessedLogMapper syncProcessedLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SyncResultDTO> batchSync(List<SyncItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<SyncResultDTO> results = new ArrayList<>(Collections.nCopies(items.size(), null));
        List<WorkItem> toProcess = new ArrayList<>();
        Map<String, WorkItem> freshByDedupeKey = new HashMap<>();
        Map<String, List<WorkItem>> duplicatesByDedupeKey = new HashMap<>();

        for (int i = 0; i < items.size(); i++) {
            WorkItem work = new WorkItem(items.get(i), i);
            String dedupeKey = work.getDedupeKey();
            if (dedupeKey != null) {
                SyncProcessedLog processed = syncProcessedLogMapper.selectByClientIdAndLocalId(
                        work.getClientId(), work.getLocalId());
                if (processed != null) {
                    results.set(i, rememberedResult(work, processed));
                    continue;
                }

                WorkItem first = freshByDedupeKey.get(dedupeKey);
                if (first != null) {
                    duplicatesByDedupeKey.computeIfAbsent(dedupeKey, key -> new ArrayList<>()).add(work);
                    continue;
                }
                freshByDedupeKey.put(dedupeKey, work);
            }
            toProcess.add(work);
        }

        toProcess.sort(Comparator.comparing(
                work -> work.item.getCreatedAt(),
                Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, BigDecimal> simQty = new HashMap<>();
        List<SyncResultDTO> phase1 = new ArrayList<>(Collections.nCopies(toProcess.size(), null));
        boolean hasError = false;
        for (int i = 0; i < toProcess.size(); i++) {
            WorkItem work = toProcess.get(i);
            SyncItemDTO item = work.item;
            String key = item.getWarehouseId() + ":" + item.getProductId();
            if (!simQty.containsKey(key)) {
                StockSnapshot snap = stockSnapshotMapper.selectOne(item.getProductId(), item.getWarehouseId());
                simQty.put(key, snap != null ? snap.getCurrentQty() : BigDecimal.ZERO);
            }

            int absQty = Math.abs(item.getQty());
            boolean isOut = "OUT".equals(item.getType()) || item.getQty() < 0;
            BigDecimal current = simQty.get(key);
            if (isOut && current.compareTo(BigDecimal.valueOf(absQty)) < 0) {
                phase1.set(i, SyncResultDTO.fail(work.originalIndex, work.getClientId(), work.getLocalId(),
                        "库存不足，批次模拟库存：" + current.toPlainString() + "，需要：" + absQty));
                hasError = true;
            } else {
                BigDecimal delta = BigDecimal.valueOf(isOut ? -absQty : absQty);
                simQty.put(key, current.add(delta));
            }
        }

        if (hasError) {
            for (int i = 0; i < toProcess.size(); i++) {
                WorkItem work = toProcess.get(i);
                SyncResultDTO result = phase1.get(i);
                if (result == null) {
                    result = SyncResultDTO.fail(work.originalIndex, work.getClientId(), work.getLocalId(),
                            "批次内存在库存不足，整批已拒绝");
                }
                completeResult(work, result, results, duplicatesByDedupeKey);
            }
            return results;
        }

        for (WorkItem work : toProcess) {
            SyncItemDTO item = work.item;
            int absQty = Math.abs(item.getQty());
            boolean isOut = "OUT".equals(item.getType()) || item.getQty() < 0;
            int delta = isOut ? -absQty : absQty;

            StockSnapshot snap = stockSnapshotMapper.selectOneForUpdate(item.getProductId(), item.getWarehouseId());
            BigDecimal beforeQty = snap != null ? snap.getCurrentQty() : BigDecimal.ZERO;

            if (isOut && beforeQty.compareTo(BigDecimal.valueOf(absQty)) < 0) {
                throw new BusinessException("库存在校验期间被并发修改，请稍后重试");
            }

            InventoryLedger ledger = new InventoryLedger();
            ledger.setId(UUID.randomUUID().toString());
            ledger.setProductId(item.getProductId());
            ledger.setLocationId(item.getWarehouseId());
            ledger.setChangeQty(BigDecimal.valueOf(delta));
            ledger.setType(isOut ? "outbound" : "inbound");
            ledger.setOperator("");
            ledger.setNote(item.getRemark());
            ledger.setOccurredAt(item.getCreatedAt() != null ? item.getCreatedAt() : LocalDateTime.now());
            ledger.setSynced(1);
            ledger.setCreatedAt(LocalDateTime.now());
            inventoryLedgerMapper.insert(ledger);

            BigDecimal newQty = beforeQty.add(BigDecimal.valueOf(delta));
            stockSnapshotMapper.upsert(
                item.getProductId(),
                item.getWarehouseId(),
                newQty,
                snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0
            );

            completeResult(work, SyncResultDTO.ok(work.originalIndex, work.getClientId(), work.getLocalId()),
                    results, duplicatesByDedupeKey);
        }

        return results;
    }

    private SyncResultDTO rememberedResult(WorkItem work, SyncProcessedLog processed) {
        if (Boolean.TRUE.equals(processed.getSuccess())) {
            return SyncResultDTO.ok(work.originalIndex, work.getClientId(), work.getLocalId());
        }
        return SyncResultDTO.fail(work.originalIndex, work.getClientId(), work.getLocalId(),
                processed.getRejectReason());
    }

    private void completeResult(WorkItem work,
                                SyncResultDTO result,
                                List<SyncResultDTO> results,
                                Map<String, List<WorkItem>> duplicatesByDedupeKey) {
        results.set(work.originalIndex, result);
        if (result.isSuccess()) {
            rememberSuccess(work);
        }

        String dedupeKey = work.getDedupeKey();
        if (dedupeKey == null) {
            return;
        }
        List<WorkItem> duplicates = duplicatesByDedupeKey.get(dedupeKey);
        if (duplicates == null) {
            return;
        }
        for (WorkItem duplicate : duplicates) {
            SyncResultDTO duplicateResult = result.isSuccess()
                    ? SyncResultDTO.ok(duplicate.originalIndex, duplicate.getClientId(), duplicate.getLocalId())
                    : SyncResultDTO.fail(duplicate.originalIndex, duplicate.getClientId(), duplicate.getLocalId(),
                            result.getRejectReason());
            results.set(duplicate.originalIndex, duplicateResult);
        }
    }

    private void rememberSuccess(WorkItem work) {
        if (work.getDedupeKey() == null) {
            return;
        }

        SyncProcessedLog log = new SyncProcessedLog();
        log.setClientId(work.getClientId());
        log.setLocalId(work.getLocalId());
        log.setSuccess(true);
        LocalDateTime now = LocalDateTime.now();
        log.setCreatedAt(now);
        log.setProcessedAt(now);
        syncProcessedLogMapper.insert(log);
    }

    private static class WorkItem {
        private final SyncItemDTO item;
        private final int originalIndex;
        private final String clientId;

        private WorkItem(SyncItemDTO item, int originalIndex) {
            this.item = item;
            this.originalIndex = originalIndex;
            this.clientId = normalizeClientId(item.getClientId());
        }

        private Long getLocalId() {
            return item.getLocalId();
        }

        private String getClientId() {
            return clientId;
        }

        private String getDedupeKey() {
            Long localId = getLocalId();
            // 不带 clientId 的旧客户端请求不参与去重：各设备 localId 都从 1 自增，
            // 跨设备按 localId 去重会把不同设备的流水误判为重复而静默丢账
            if (clientId == null || localId == null) {
                return null;
            }
            return clientId + "\u0000" + localId;
        }

        private static String normalizeClientId(String clientId) {
            if (clientId == null || clientId.trim().isEmpty()) {
                return null;
            }
            return clientId.trim();
        }
    }
}
