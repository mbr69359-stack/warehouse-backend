package com.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.ProductDTO;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.common.BusinessException;
import com.warehouse.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final StockSnapshotMapper stockSnapshotMapper;
    private final InventoryLedgerMapper ledgerMapper;

    @Override
    public Page<Product> page(int current, int size, String name, Long categoryId) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .like(StringUtils.hasText(name), Product::getName, name)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .orderByDesc(Product::getCreateTime);
        return productMapper.selectPage(new Page<>(current, size), q);
    }

    @Override
    public void create(ProductDTO dto) {
        Product p = new Product();
        p.setName(dto.getName()); p.setSkuCode(dto.getSkuCode());
        p.setCategoryId(dto.getCategoryId()); p.setUnit(dto.getUnit());
        p.setPrice(dto.getPrice()); p.setImage(dto.getImage());
        p.setRemark(dto.getRemark());
        p.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        productMapper.insert(p);
    }

    @Override
    @Transactional
    public String update(ProductDTO dto) {
        Product p = productMapper.selectByIdForUpdate(dto.getId());
        if (p == null) throw new BusinessException("商品不存在");

        boolean hadQtyPerBox = p.getQtyPerBox() != null && p.getQtyPerBox() > 0;
        boolean newQtyValid = dto.getQtyPerBox() != null && dto.getQtyPerBox() > 0;
        // 库存换算只允许发生一次（箱→个）；已设过再改只更新字段，避免对已是"个"的库存重复乘
        boolean firstTimeSet = !hadQtyPerBox && newQtyValid;
        boolean changedExisting = hadQtyPerBox && newQtyValid
                && !dto.getQtyPerBox().equals(p.getQtyPerBox());

        p.setName(dto.getName()); p.setCategoryId(dto.getCategoryId());
        p.setUnit(dto.getUnit()); p.setPrice(dto.getPrice());
        p.setImage(dto.getImage()); p.setRemark(dto.getRemark());
        if (dto.getStatus() != null) p.setStatus(dto.getStatus());
        if (newQtyValid) p.setQtyPerBox(dto.getQtyPerBox());
        productMapper.updateById(p);

        if (firstTimeSet) {
            migrateBoxInventoryToPiece(p.getId(), dto.getQtyPerBox());
        }
        if (changedExisting) {
            return "每箱个数已修改，历史库存不会自动换算，如有偏差请通过盘点调整";
        }
        return null;
    }

    private void migrateBoxInventoryToPiece(Long productId, int qtyPerBox) {
        List<StockSnapshot> snaps = stockSnapshotMapper.selectBoxWarehouseSnapshotsForUpdate(productId);
        for (StockSnapshot snap : snaps) {
            BigDecimal oldQty = snap.getCurrentQty() != null ? snap.getCurrentQty() : BigDecimal.ZERO;
            BigDecimal delta = oldQty.multiply(BigDecimal.valueOf(qtyPerBox - 1));
            if (delta.compareTo(BigDecimal.ZERO) == 0) continue;

            InventoryLedger entry = new InventoryLedger();
            entry.setId(UUID.randomUUID().toString());
            entry.setProductId(productId);
            entry.setLocationId(snap.getLocationId());
            entry.setChangeQty(delta);
            entry.setType("unit_migration");
            entry.setQtyUnit("PIECE");
            entry.setNote("单位迁移：1箱=" + qtyPerBox + "个，原qty=" + oldQty.intValue() + "箱");
            entry.setOccurredAt(LocalDateTime.now());
            entry.setSynced(1);
            entry.setCreatedAt(LocalDateTime.now());
            ledgerMapper.insert(entry);

            BigDecimal newQty = oldQty.multiply(BigDecimal.valueOf(qtyPerBox));
            stockSnapshotMapper.upsert(productId, snap.getLocationId(), newQty,
                    snap.getAlertQty() != null ? snap.getAlertQty() : 0);
        }
    }

    @Override
    public void delete(Long id) {
        java.math.BigDecimal stock = stockSnapshotMapper.selectTotalQtyByProductId(id);
        if (stock.compareTo(java.math.BigDecimal.ZERO) > 0)
            throw new BusinessException("该商品仍有库存 " + stock.intValue() + " 件，无法删除");
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .eq(Product::getDeleted, 0)
                .set(Product::getDeleted, 1)
                .set(Product::getUpdateTime, LocalDateTime.now()));
    }
}
