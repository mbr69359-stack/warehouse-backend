package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.InventoryCheckDTO;
import com.warehouse.entity.Inventory;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.common.BusinessException;
import com.warehouse.service.InventoryService;
import com.warehouse.vo.InventoryChartItemVO;
import com.warehouse.vo.InventoryStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final ConcurrentHashMap<Long, Boolean> CHECKING = new ConcurrentHashMap<>();

    private final InventoryMapper inventoryMapper;
    private final InventoryLedgerMapper inventoryLedgerMapper;
    private final StockSnapshotMapper stockSnapshotMapper;

    @Override
    public Page<Inventory> page(int current, int size, Long warehouseId, Long productId, LocalDateTime updatedAfter) {
        return inventoryMapper.selectPageFromSnapshot(new Page<>(current, size), warehouseId, productId, updatedAfter);
    }

    @Override
    public List<Inventory> listAlerts() {
        return inventoryMapper.selectAlertsFromSnapshot();
    }

    @Override
    @Transactional
    public void check(InventoryCheckDTO dto) {
        if (dto.getItems() == null) return;
        Long warehouseId = dto.getWarehouseId();
        if (CHECKING.putIfAbsent(warehouseId, Boolean.TRUE) != null) {
            throw new BusinessException("该仓库正在盘点中，请等待操作完成后再提交");
        }
        try {
            for (InventoryCheckDTO.CheckItem ci : dto.getItems()) {
                int actualQty = ci.getActualQty() != null ? ci.getActualQty() : 0;
                StockSnapshot snap = stockSnapshotMapper.selectOne(ci.getProductId(), warehouseId);
                int beforeQty = snap != null && snap.getCurrentQty() != null
                        ? snap.getCurrentQty().intValue() : 0;
                int alertQty = snap != null && snap.getAlertQty() != null ? snap.getAlertQty() : 0;
                int delta = actualQty - beforeQty;
                if (delta != 0) {
                    InventoryLedger ledger = new InventoryLedger();
                    ledger.setId(UUID.randomUUID().toString());
                    ledger.setProductId(ci.getProductId());
                    ledger.setLocationId(warehouseId);
                    ledger.setChangeQty(BigDecimal.valueOf(delta));
                    ledger.setType("adjust");
                    ledger.setOperator("");
                    ledger.setNote(dto.getRemark());
                    ledger.setOccurredAt(LocalDateTime.now());
                    ledger.setSynced(1);
                    ledger.setCreatedAt(LocalDateTime.now());
                    inventoryLedgerMapper.insert(ledger);
                }
                stockSnapshotMapper.upsert(
                    ci.getProductId(), warehouseId,
                    BigDecimal.valueOf(actualQty),
                    alertQty
                );
            }
        } finally {
            CHECKING.remove(warehouseId);
        }
    }

    @Override
    public void setAlertQty(Long warehouseId, Long productId, Integer alertQty) {
        if (alertQty == null || alertQty < 0) throw new BusinessException("预警数量不能为负数");
        try {
            StockSnapshot snap = stockSnapshotMapper.selectOne(productId, warehouseId);
            if (snap == null) throw new BusinessException("库存记录不存在，请先入库");
            stockSnapshotMapper.updateAlertQty(productId, warehouseId, alertQty);
            inventoryMapper.update(null, new LambdaUpdateWrapper<Inventory>()
                    .eq(Inventory::getWarehouseId, warehouseId)
                    .eq(Inventory::getProductId, productId)
                    .set(Inventory::getAlertQty, alertQty));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("操作库存记录异常：" + e.getMessage());
        }
    }

    @Override
    public InventoryStatsVO getStats() {
        InventoryStatsVO result = new InventoryStatsVO();
        Long total = inventoryMapper.selectTotalQtyFromSnapshot();
        result.setTotalQty(total != null ? total : 0L);
        InventoryStatsVO maxW = inventoryMapper.selectMaxWarehouseFromSnapshot();
        if (maxW != null) {
            result.setMaxWarehouseName(maxW.getMaxWarehouseName());
            result.setMaxWarehouseQty(maxW.getMaxWarehouseQty());
            result.setMaxWarehouseId(maxW.getMaxWarehouseId());
        }
        return result;
    }

    @Override
    public List<InventoryChartItemVO> getChartData(String type, Long warehouseId) {
        List<InventoryChartItemVO> items;
        if ("warehouse".equals(type) && warehouseId != null) {
            items = inventoryMapper.selectChartByWarehouseFromSnapshot(warehouseId);
        } else {
            items = inventoryMapper.selectChartAllFromSnapshot();
        }
        items.forEach(item -> item.setIsLow(
            item.getAlertQty() != null && item.getAlertQty() > 0 &&
            item.getQty() != null && item.getQty() < item.getAlertQty()
        ));
        return items;
    }
}
