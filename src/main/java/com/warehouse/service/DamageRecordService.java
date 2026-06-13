package com.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.dto.DamageRecordDTO;
import com.warehouse.dto.DamageTransferDTO;
import com.warehouse.entity.DamageRecord;

import java.util.List;

public interface DamageRecordService {
    Page<DamageRecord> page(int current, int size, String status, Long warehouseId);
    Long create(DamageRecordDTO dto, String createdBy);
    long countPendingAvailable(Long warehouseId);
    List<DamageRecord> listPendingAvailable(Long warehouseId);
    void delete(Long id);
    void transfer(Long damageRecordId, DamageTransferDTO dto, String operator);
    void writeOff(Long id, String operator);
}
