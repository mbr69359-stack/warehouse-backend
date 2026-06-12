package com.warehouse.service;

import com.warehouse.dto.SyncItemDTO;
import com.warehouse.dto.SyncResultDTO;
import com.warehouse.entity.InventoryLedger;
import com.warehouse.entity.StockSnapshot;
import com.warehouse.entity.SyncProcessedLog;
import com.warehouse.mapper.InventoryLedgerMapper;
import com.warehouse.mapper.StockSnapshotMapper;
import com.warehouse.mapper.SyncProcessedLogMapper;
import com.warehouse.service.impl.SyncServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncServiceImplTest {

    @Mock
    private InventoryLedgerMapper inventoryLedgerMapper;

    @Mock
    private StockSnapshotMapper stockSnapshotMapper;

    @Mock
    private SyncProcessedLogMapper syncProcessedLogMapper;

    private SyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncServiceImpl(inventoryLedgerMapper, stockSnapshotMapper, syncProcessedLogMapper);
    }

    @Test
    void replayingSameClientIdAndLocalIdDoesNotWriteLedgerOrSnapshotAgain() {
        when(syncProcessedLogMapper.selectByClientIdAndLocalId("client-a", 100L))
                .thenReturn(null, processed("client-a", 100L));
        when(stockSnapshotMapper.selectOne(1L, 1L)).thenReturn(snapshot("10"));
        when(stockSnapshotMapper.selectOneForUpdate(1L, 1L)).thenReturn(snapshot("10"));

        List<SyncResultDTO> first = service.batchSync(Collections.singletonList(item("client-a", 100L, "IN", 5,
                LocalDateTime.of(2026, 6, 10, 10, 0))));
        List<SyncResultDTO> replay = service.batchSync(Collections.singletonList(item("client-a", 100L, "IN", 5,
                LocalDateTime.of(2026, 6, 10, 10, 0))));

        assertThat(first).hasSize(1);
        assertThat(first.get(0).isSuccess()).isTrue();
        assertThat(first.get(0).getClientId()).isEqualTo("client-a");
        assertThat(first.get(0).getLocalId()).isEqualTo(100L);
        assertThat(replay).hasSize(1);
        assertThat(replay.get(0).isSuccess()).isTrue();
        assertThat(replay.get(0).getClientId()).isEqualTo("client-a");
        assertThat(replay.get(0).getLocalId()).isEqualTo(100L);
        verify(inventoryLedgerMapper, times(1)).insert(any(InventoryLedger.class));
        verify(stockSnapshotMapper, times(1)).upsert(1L, 1L, new BigDecimal("15"), 0);

        ArgumentCaptor<SyncProcessedLog> logCaptor = ArgumentCaptor.forClass(SyncProcessedLog.class);
        verify(syncProcessedLogMapper, times(1)).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getClientId()).isEqualTo("client-a");
        assertThat(logCaptor.getValue().getLocalId()).isEqualTo(100L);
        assertThat(logCaptor.getValue().getSuccess()).isTrue();
    }

    @Test
    void sameClientIdAndLocalIdInSameBatchWritesOnceAndReturnsBothResults() {
        when(syncProcessedLogMapper.selectByClientIdAndLocalId("client-a", 101L)).thenReturn(null);
        when(stockSnapshotMapper.selectOne(1L, 1L)).thenReturn(snapshot("10"));
        when(stockSnapshotMapper.selectOneForUpdate(1L, 1L)).thenReturn(snapshot("10"));

        List<SyncResultDTO> results = service.batchSync(Arrays.asList(
                item("client-a", 101L, "IN", 5, LocalDateTime.of(2026, 6, 10, 10, 0)),
                item("client-a", 101L, "IN", 5, LocalDateTime.of(2026, 6, 10, 10, 1))
        ));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(SyncResultDTO::isSuccess).containsExactly(true, true);
        assertThat(results).extracting(SyncResultDTO::getClientId).containsExactly("client-a", "client-a");
        assertThat(results).extracting(SyncResultDTO::getLocalId).containsExactly(101L, 101L);
        verify(inventoryLedgerMapper, times(1)).insert(any(InventoryLedger.class));
        verify(stockSnapshotMapper, times(1)).upsert(1L, 1L, new BigDecimal("15"), 0);
        verify(syncProcessedLogMapper, times(1)).insert(any(SyncProcessedLog.class));
    }

    @Test
    void processedSuccessReturnsSuccessWithoutInventoryWrites() {
        when(syncProcessedLogMapper.selectByClientIdAndLocalId("client-a", 100L))
                .thenReturn(processed("client-a", 100L));

        List<SyncResultDTO> replay = service.batchSync(Collections.singletonList(item("client-a", 100L, "OUT", -5,
                LocalDateTime.of(2026, 6, 10, 10, 0))));

        assertThat(replay).hasSize(1);
        assertThat(replay.get(0).isSuccess()).isTrue();
        assertThat(replay.get(0).getClientId()).isEqualTo("client-a");
        assertThat(replay.get(0).getLocalId()).isEqualTo(100L);
        verifyNoInteractions(inventoryLedgerMapper, stockSnapshotMapper);
        verify(syncProcessedLogMapper, never()).insert(any(SyncProcessedLog.class));
    }

    @Test
    void resultsKeepOriginalLocalIdsWhenExecutionIsSortedByCreatedAt() {
        SyncItemDTO laterInputFirst = item("client-a", 200L, "IN", 5,
                LocalDateTime.of(2026, 6, 10, 12, 0));
        SyncItemDTO earlierInputSecond = item("client-a", 201L, "OUT", -3,
                LocalDateTime.of(2026, 6, 10, 9, 0));
        when(syncProcessedLogMapper.selectByClientIdAndLocalId("client-a", 200L)).thenReturn(null);
        when(syncProcessedLogMapper.selectByClientIdAndLocalId("client-a", 201L)).thenReturn(null);
        when(stockSnapshotMapper.selectOne(1L, 1L)).thenReturn(snapshot("10"));
        when(stockSnapshotMapper.selectOneForUpdate(1L, 1L))
                .thenReturn(snapshot("10"), snapshot("7"));

        List<SyncResultDTO> results = service.batchSync(Arrays.asList(laterInputFirst, earlierInputSecond));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(SyncResultDTO::getIndex).containsExactly(0, 1);
        assertThat(results).extracting(SyncResultDTO::getClientId).containsExactly("client-a", "client-a");
        assertThat(results).extracting(SyncResultDTO::getLocalId).containsExactly(200L, 201L);
        assertThat(results).extracting(SyncResultDTO::isSuccess).containsExactly(true, true);

        ArgumentCaptor<InventoryLedger> ledgerCaptor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(inventoryLedgerMapper, times(2)).insert(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getAllValues())
                .extracting(InventoryLedger::getChangeQty)
                .containsExactly(new BigDecimal("-3"), new BigDecimal("5"));
    }

    @Test
    void insufficientStockFailureIsNotRememberedAndRetryRevalidates() {
        when(syncProcessedLogMapper.selectByClientIdAndLocalId("client-a", 300L)).thenReturn(null, null);
        when(stockSnapshotMapper.selectOne(1L, 1L)).thenReturn(snapshot("1"), snapshot("10"));
        when(stockSnapshotMapper.selectOneForUpdate(1L, 1L)).thenReturn(snapshot("10"));

        List<SyncResultDTO> first = service.batchSync(Collections.singletonList(item("client-a", 300L, "OUT", -5,
                LocalDateTime.of(2026, 6, 10, 10, 0))));

        assertThat(first).hasSize(1);
        assertThat(first.get(0).isSuccess()).isFalse();
        assertThat(first.get(0).getClientId()).isEqualTo("client-a");
        assertThat(first.get(0).getLocalId()).isEqualTo(300L);
        assertThat(first.get(0).getRejectReason()).contains("库存不足");
        verify(syncProcessedLogMapper, never()).insert(any(SyncProcessedLog.class));
        verify(inventoryLedgerMapper, never()).insert(any(InventoryLedger.class));
        verify(stockSnapshotMapper, never()).upsert(any(), any(), any(), anyInt());

        List<SyncResultDTO> retry = service.batchSync(Collections.singletonList(item("client-a", 300L, "OUT", -5,
                LocalDateTime.of(2026, 6, 10, 10, 0))));

        assertThat(retry).hasSize(1);
        assertThat(retry.get(0).isSuccess()).isTrue();
        assertThat(retry.get(0).getClientId()).isEqualTo("client-a");
        assertThat(retry.get(0).getLocalId()).isEqualTo(300L);
        verify(inventoryLedgerMapper, times(1)).insert(any(InventoryLedger.class));
        verify(stockSnapshotMapper, times(1)).upsert(1L, 1L, new BigDecimal("5"), 0);

        ArgumentCaptor<SyncProcessedLog> logCaptor = ArgumentCaptor.forClass(SyncProcessedLog.class);
        verify(syncProcessedLogMapper, times(1)).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getClientId()).isEqualTo("client-a");
        assertThat(logCaptor.getValue().getSuccess()).isTrue();
    }

    @Test
    void sameLocalIdFromDifferentClientsBothRecorded() {
        when(syncProcessedLogMapper.selectByClientIdAndLocalId("phone", 3L)).thenReturn(null);
        when(syncProcessedLogMapper.selectByClientIdAndLocalId("pc", 3L)).thenReturn(null);
        when(stockSnapshotMapper.selectOne(1L, 1L)).thenReturn(snapshot("10"), snapshot("15"));
        when(stockSnapshotMapper.selectOneForUpdate(1L, 1L)).thenReturn(snapshot("10"), snapshot("15"));

        List<SyncResultDTO> phone = service.batchSync(Collections.singletonList(item("phone", 3L, "IN", 5,
                LocalDateTime.of(2026, 6, 10, 9, 0))));
        List<SyncResultDTO> pc = service.batchSync(Collections.singletonList(item("pc", 3L, "IN", 5,
                LocalDateTime.of(2026, 6, 10, 10, 0))));

        assertThat(phone).hasSize(1);
        assertThat(phone.get(0).isSuccess()).isTrue();
        assertThat(phone.get(0).getClientId()).isEqualTo("phone");
        assertThat(phone.get(0).getLocalId()).isEqualTo(3L);
        assertThat(pc).hasSize(1);
        assertThat(pc.get(0).isSuccess()).isTrue();
        assertThat(pc.get(0).getClientId()).isEqualTo("pc");
        assertThat(pc.get(0).getLocalId()).isEqualTo(3L);

        verify(inventoryLedgerMapper, times(2)).insert(any(InventoryLedger.class));
        verify(stockSnapshotMapper, times(1)).upsert(1L, 1L, new BigDecimal("15"), 0);
        verify(stockSnapshotMapper, times(1)).upsert(1L, 1L, new BigDecimal("20"), 0);

        ArgumentCaptor<SyncProcessedLog> logCaptor = ArgumentCaptor.forClass(SyncProcessedLog.class);
        verify(syncProcessedLogMapper, times(2)).insert(logCaptor.capture());
        assertThat(logCaptor.getAllValues())
                .extracting(SyncProcessedLog::getClientId)
                .containsExactlyInAnyOrder("phone", "pc");
        assertThat(logCaptor.getAllValues())
                .extracting(SyncProcessedLog::getLocalId)
                .containsExactly(3L, 3L);
    }

    @Test
    void missingClientIdWithSameLocalIdFromDifferentDevicesBothRecorded() {
        // 设备A localId=5 已入账后，设备B localId=5（同样不带 clientId）再同步，不能被误判为重复而丢账
        when(stockSnapshotMapper.selectOne(1L, 1L)).thenReturn(snapshot("10"), snapshot("15"));
        when(stockSnapshotMapper.selectOneForUpdate(1L, 1L)).thenReturn(snapshot("10"), snapshot("15"));

        List<SyncResultDTO> deviceA = service.batchSync(Collections.singletonList(item(null, 5L, "IN", 5,
                LocalDateTime.of(2026, 6, 10, 9, 0))));
        List<SyncResultDTO> deviceB = service.batchSync(Collections.singletonList(item(null, 5L, "IN", 5,
                LocalDateTime.of(2026, 6, 10, 10, 0))));

        assertThat(deviceA).hasSize(1);
        assertThat(deviceA.get(0).isSuccess()).isTrue();
        assertThat(deviceA.get(0).getClientId()).isNull();
        assertThat(deviceA.get(0).getLocalId()).isEqualTo(5L);
        assertThat(deviceB).hasSize(1);
        assertThat(deviceB.get(0).isSuccess()).isTrue();
        assertThat(deviceB.get(0).getClientId()).isNull();
        assertThat(deviceB.get(0).getLocalId()).isEqualTo(5L);

        verify(inventoryLedgerMapper, times(2)).insert(any(InventoryLedger.class));
        verify(stockSnapshotMapper, times(1)).upsert(1L, 1L, new BigDecimal("15"), 0);
        verify(stockSnapshotMapper, times(1)).upsert(1L, 1L, new BigDecimal("20"), 0);
        // 不带 clientId 完全不走幂等日志：既不查询也不写入
        verifyNoInteractions(syncProcessedLogMapper);
    }

    @Test
    void missingClientIdWithSameLocalIdInSameBatchBothRecorded() {
        when(stockSnapshotMapper.selectOne(1L, 1L)).thenReturn(snapshot("10"));
        when(stockSnapshotMapper.selectOneForUpdate(1L, 1L)).thenReturn(snapshot("10"), snapshot("15"));

        List<SyncResultDTO> results = service.batchSync(Arrays.asList(
                item(null, 5L, "IN", 5, LocalDateTime.of(2026, 6, 10, 9, 0)),
                item(null, 5L, "IN", 5, LocalDateTime.of(2026, 6, 10, 10, 0))
        ));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(SyncResultDTO::isSuccess).containsExactly(true, true);
        assertThat(results).extracting(SyncResultDTO::getLocalId).containsExactly(5L, 5L);

        verify(inventoryLedgerMapper, times(2)).insert(any(InventoryLedger.class));
        verify(stockSnapshotMapper, times(1)).upsert(1L, 1L, new BigDecimal("15"), 0);
        verify(stockSnapshotMapper, times(1)).upsert(1L, 1L, new BigDecimal("20"), 0);
        verifyNoInteractions(syncProcessedLogMapper);
    }

    private SyncItemDTO item(String clientId, Long localId, String type, int qty, LocalDateTime createdAt) {
        SyncItemDTO item = new SyncItemDTO();
        item.setClientId(clientId);
        item.setLocalId(localId);
        item.setType(type);
        item.setWarehouseId(1L);
        item.setProductId(1L);
        item.setQty(qty);
        item.setRemark("offline");
        item.setCreatedAt(createdAt);
        return item;
    }

    private SyncProcessedLog processed(String clientId, Long localId) {
        SyncProcessedLog log = new SyncProcessedLog();
        log.setClientId(clientId);
        log.setLocalId(localId);
        log.setSuccess(true);
        return log;
    }

    private StockSnapshot snapshot(String currentQty) {
        StockSnapshot snapshot = new StockSnapshot();
        snapshot.setProductId(1L);
        snapshot.setLocationId(1L);
        snapshot.setCurrentQty(new BigDecimal(currentQty));
        snapshot.setAlertQty(0);
        return snapshot;
    }
}
