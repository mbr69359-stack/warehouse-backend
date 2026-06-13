package com.warehouse.service;

import com.warehouse.common.BusinessException;
import com.warehouse.dto.DamageRecordDTO;
import com.warehouse.entity.DamageRecord;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.mapper.DamageRecordMapper;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.ProductMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.mapper.WarehouseMapper;
import com.warehouse.service.impl.DamageRecordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DamageRecordServiceImplTest {

    @Mock private DamageRecordMapper damageRecordMapper;
    @Mock private ProductMapper productMapper;
    @Mock private WarehouseMapper warehouseMapper;
    @Mock private StockSnapshotMapper snapshotMapper;
    @Mock private InventoryLedgerMapper ledgerMapper;

    private DamageRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DamageRecordServiceImpl(
                damageRecordMapper, productMapper, warehouseMapper, snapshotMapper, ledgerMapper);
    }

    private Product product(Long id, Integer qtyPerBox, String cost) {
        Product p = new Product();
        p.setId(id);
        p.setQtyPerBox(qtyPerBox);
        p.setCostPrice(cost == null ? null : new BigDecimal(cost));
        return p;
    }

    private StockSnapshot snapshot(Long productId, Long whId, String currentQty, Integer alertQty) {
        StockSnapshot s = new StockSnapshot();
        s.setProductId(productId);
        s.setLocationId(whId);
        s.setCurrentQty(new BigDecimal(currentQty));
        s.setAlertQty(alertQty);
        return s;
    }

    private DamageRecordDTO dto(Long whId, Long productId, Integer qty, String unit) {
        DamageRecordDTO d = new DamageRecordDTO();
        d.setWarehouseId(whId);
        d.setProductId(productId);
        d.setQty(qty);
        d.setUnit(unit);
        return d;
    }

    @Test
    void create_boxUnit_convertsBoxesToPieces() {
        when(warehouseMapper.selectTypeById(1L)).thenReturn("BOX");
        when(productMapper.selectById(2L)).thenReturn(product(2L, 24, "3.00"));
        when(snapshotMapper.selectOneForUpdate(2L, 1L)).thenReturn(snapshot(2L, 1L, "100", 5));

        service.create(dto(1L, 2L, 2, "BOX"), "alice");

        ArgumentCaptor<DamageRecord> recCaptor = ArgumentCaptor.forClass(DamageRecord.class);
        verify(damageRecordMapper).insert(recCaptor.capture());
        assertThat(recCaptor.getValue().getQty()).isEqualTo(48);

        ArgumentCaptor<InventoryLedger> ledCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(ledgerMapper).insert(ledCaptor.capture());
        assertThat(ledCaptor.getValue().getChangeQty()).isEqualByComparingTo("-48");
        assertThat(ledCaptor.getValue().getType()).isEqualTo("damage");

        verify(snapshotMapper).upsert(2L, 1L, new BigDecimal("52"), 5);
    }

    @Test
    void create_pieceUnit_keepsQtyAsPieces() {
        when(warehouseMapper.selectTypeById(1L)).thenReturn("PIECE");
        when(snapshotMapper.selectOneForUpdate(2L, 1L)).thenReturn(snapshot(2L, 1L, "100", 0));

        service.create(dto(1L, 2L, 5, null), "alice");

        ArgumentCaptor<DamageRecord> recCaptor = ArgumentCaptor.forClass(DamageRecord.class);
        verify(damageRecordMapper).insert(recCaptor.capture());
        assertThat(recCaptor.getValue().getQty()).isEqualTo(5);
        verify(snapshotMapper).upsert(2L, 1L, new BigDecimal("95"), 0);
    }

    @Test
    void create_boxUnit_onPieceWarehouse_throws() {
        when(warehouseMapper.selectTypeById(1L)).thenReturn("PIECE");

        assertThatThrownBy(() -> service.create(dto(1L, 2L, 2, "BOX"), "alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("按个仓库不支持按箱");

        verify(snapshotMapper, never()).upsert(anyLong(), anyLong(), any(BigDecimal.class), anyInt());
    }

    private DamageRecord record(Long id, String status, Long productId, Integer qty, Long outOrderId) {
        DamageRecord r = new DamageRecord();
        r.setId(id);
        r.setStatus(status);
        r.setProductId(productId);
        r.setQty(qty);
        r.setOutOrderId(outOrderId);
        return r;
    }

    @Test
    void writeOff_resolvesRecordWithoutTouchingStock() {
        when(damageRecordMapper.selectByIdForUpdate(10L))
                .thenReturn(record(10L, "PENDING", 2L, 48, null));
        when(productMapper.selectById(2L)).thenReturn(product(2L, 24, "3.00"));

        service.writeOff(10L, "alice");

        ArgumentCaptor<DamageRecord> captor = ArgumentCaptor.forClass(DamageRecord.class);
        verify(damageRecordMapper).updateById(captor.capture());
        DamageRecord saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("RESOLVED");
        assertThat(saved.getCostDeduction()).isEqualByComparingTo("144.00");
        assertThat(saved.getGoodQty()).isEqualTo(0);
        assertThat(saved.getResolvedAt()).isNotNull();

        verify(snapshotMapper, never()).upsert(anyLong(), anyLong(), any(BigDecimal.class), anyInt());
        verify(ledgerMapper, never()).insert(any(InventoryLedger.class));
    }

    @Test
    void writeOff_alreadyResolved_throws() {
        when(damageRecordMapper.selectByIdForUpdate(10L))
                .thenReturn(record(10L, "RESOLVED", 2L, 48, null));

        assertThatThrownBy(() -> service.writeOff(10L, "alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已处理");
    }

    @Test
    void writeOff_boundToOutOrder_throws() {
        when(damageRecordMapper.selectByIdForUpdate(10L))
                .thenReturn(record(10L, "PENDING", 2L, 48, 5L));

        assertThatThrownBy(() -> service.writeOff(10L, "alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("损坏出库单");
    }
}
