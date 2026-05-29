package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.InventoryCheckDTO;
import com.warehouse.entity.Inventory;
import com.warehouse.entity.InventoryLog;
import com.warehouse.mapper.InventoryLogMapper;
import com.warehouse.mapper.InventoryMapper;
import com.warehouse.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;

    @Override
    public Page<Inventory> page(int current, int size, Long warehouseId, Long productId) {
        LambdaQueryWrapper<Inventory> q = new LambdaQueryWrapper<Inventory>()
                .eq(warehouseId != null, Inventory::getWarehouseId, warehouseId)
                .eq(productId != null, Inventory::getProductId, productId);
        return inventoryMapper.selectPage(new Page<>(current, size), q);
    }

    @Override
    public List<Inventory> listAlerts() {
        return inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .gt(Inventory::getAlertQty, 0)
                .apply("qty < alert_qty"));
    }

    @Override
    @Transactional
    public void check(InventoryCheckDTO dto) {
        if (dto.getItems() == null) return;
        for (InventoryCheckDTO.CheckItem ci : dto.getItems()) {
            int actualQty = ci.getActualQty() != null ? ci.getActualQty() : 0;
            Inventory inv = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                    .eq(Inventory::getWarehouseId, dto.getWarehouseId())
                    .eq(Inventory::getProductId, ci.getProductId()));
            int beforeQty = 0;
            if (inv == null) {
                inv = new Inventory();
                inv.setWarehouseId(dto.getWarehouseId());
                inv.setProductId(ci.getProductId());
                inv.setQty(actualQty); inv.setAlertQty(0);
                inventoryMapper.insert(inv);
            } else {
                beforeQty = inv.getQty();
                inv.setQty(actualQty);
                inventoryMapper.updateById(inv);
            }
            InventoryLog log = new InventoryLog();
            log.setWarehouseId(dto.getWarehouseId());
            log.setProductId(ci.getProductId());
            log.setChangeQty(actualQty - beforeQty);
            log.setBeforeQty(beforeQty); log.setAfterQty(actualQty);
            log.setType("CHECK"); log.setRemark(dto.getRemark());
            inventoryLogMapper.insert(log);
        }
    }

    @Override
    public void setAlertQty(Long warehouseId, Long productId, Integer alertQty) {
        Inventory inv = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseId, warehouseId)
                .eq(Inventory::getProductId, productId));
        if (inv == null) throw new RuntimeException("库存记录不存在，请先入库");
        inv.setAlertQty(alertQty);
        inventoryMapper.updateById(inv);
    }
}
