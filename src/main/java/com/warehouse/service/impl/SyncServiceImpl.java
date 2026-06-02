package com.warehouse.service.impl;

import com.warehouse.dto.SyncItemDTO;
import com.warehouse.dto.SyncResultDTO;
import com.warehouse.entity.Inventory;
import com.warehouse.entity.InventoryLog;
import com.warehouse.mapper.InventoryLogMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;

    @Override
    @Transactional
    public List<SyncResultDTO> batchSync(List<SyncItemDTO> items) {
        items.sort(Comparator.comparing(SyncItemDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<SyncResultDTO> results = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SyncItemDTO item = items.get(i);
            int absQty = Math.abs(item.getQty());
            boolean isOut = "OUT".equals(item.getType()) || item.getQty() < 0;

            Inventory inv = inventoryMapper.selectForUpdate(item.getWarehouseId(), item.getProductId());
            int beforeQty = inv != null ? inv.getQty() : 0;

            if (isOut && beforeQty < absQty) {
                results.add(SyncResultDTO.fail(i,
                        "库存不足，当前：" + beforeQty + "，需要：" + absQty));
                continue;
            }

            int delta = isOut ? -absQty : absQty;
            if (isOut) {
                inventoryMapper.updateQty(item.getWarehouseId(), item.getProductId(), -absQty);
            } else {
                inventoryMapper.upsertQty(item.getWarehouseId(), item.getProductId(), absQty);
            }

            InventoryLog log = new InventoryLog();
            log.setWarehouseId(item.getWarehouseId());
            log.setProductId(item.getProductId());
            log.setChangeQty(delta);
            log.setBeforeQty(beforeQty);
            log.setAfterQty(beforeQty + delta);
            log.setType(isOut ? "OUT" : "IN");
            log.setRemark(item.getRemark());
            inventoryLogMapper.insert(log);

            results.add(SyncResultDTO.ok(i));
        }
        return results;
    }
}