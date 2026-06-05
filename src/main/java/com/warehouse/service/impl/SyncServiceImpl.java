package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.warehouse.common.BusinessException;
import com.warehouse.dto.SyncItemDTO;
import com.warehouse.dto.SyncResultDTO;
import com.warehouse.entity.Inventory;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private final InventoryMapper inventoryMapper;
    private final InventoryLedgerMapper inventoryLedgerMapper;
    private final StockSnapshotMapper stockSnapshotMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SyncResultDTO> batchSync(List<SyncItemDTO> items) {
        items.sort(Comparator.comparing(SyncItemDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // Phase 1: 全批校验（累计模拟库存，不写 DB）
        Map<String, Integer> simQty = new HashMap<>();
        List<SyncResultDTO> phase1 = new ArrayList<>();
        boolean hasError = false;
        for (int i = 0; i < items.size(); i++) {
            SyncItemDTO item = items.get(i);
            String key = item.getWarehouseId() + ":" + item.getProductId();
            if (!simQty.containsKey(key)) {
                Inventory inv = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getWarehouseId, item.getWarehouseId())
                        .eq(Inventory::getProductId, item.getProductId()));
                simQty.put(key, inv != null ? inv.getQty() : 0);
            }
            int absQty = Math.abs(item.getQty());
            boolean isOut = "OUT".equals(item.getType()) || item.getQty() < 0;
            int current = simQty.get(key);
            if (isOut && current < absQty) {
                phase1.add(SyncResultDTO.fail(i,
                        "库存不足，批次模拟库存：" + current + "，需要：" + absQty));
                hasError = true;
            } else {
                phase1.add(null); // 占位，阶段二替换
                simQty.put(key, current + (isOut ? -absQty : absQty));
            }
        }
        if (hasError) {
            // 整批拒绝：有具体原因的保留，其余标记为批次整体拒绝
            for (int i = 0; i < phase1.size(); i++) {
                if (phase1.get(i) == null) {
                    phase1.set(i, SyncResultDTO.fail(i, "批次内存在库存不足，整批已拒绝"));
                }
            }
            return phase1;
        }

        // Phase 2: 全批执行（任何步骤失败均抛异常，触发整个事务回滚）
        List<SyncResultDTO> results = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SyncItemDTO item = items.get(i);
            int absQty = Math.abs(item.getQty());
            boolean isOut = "OUT".equals(item.getType()) || item.getQty() < 0;

            // selectForUpdate：锁定行，并且在同一事务内能读到本次已提交的最新值
            Inventory inv = inventoryMapper.selectForUpdate(item.getWarehouseId(), item.getProductId());
            int beforeQty = inv != null ? inv.getQty() : 0;

            if (isOut && beforeQty < absQty) {
                // 两阶段之间库存被并发修改，整批回滚
                throw new BusinessException("库存在校验期间被并发修改，请稍后重试");
            }

            int delta = isOut ? -absQty : absQty;
            if (inv == null) {
                Inventory newInv = new Inventory();
                newInv.setWarehouseId(item.getWarehouseId());
                newInv.setProductId(item.getProductId());
                newInv.setQty(absQty);
                newInv.setAlertQty(0);
                inventoryMapper.insert(newInv);
            } else {
                inv.setQty(beforeQty + delta);
                if (inventoryMapper.updateById(inv) == 0)
                    throw new BusinessException("库存并发冲突，请稍后重试");
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

            int newQty = beforeQty + delta;
            stockSnapshotMapper.upsert(
                item.getProductId(), item.getWarehouseId(),
                BigDecimal.valueOf(newQty),
                inv != null && inv.getAlertQty() != null ? inv.getAlertQty() : 0
            );

            results.add(SyncResultDTO.ok(i));
        }
        return results;
    }
}