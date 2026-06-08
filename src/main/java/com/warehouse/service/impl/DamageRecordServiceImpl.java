package com.warehouse.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.warehouse.common.BusinessException;
import com.warehouse.dto.DamageRecordDTO;
import com.warehouse.entity.DamageRecord;
import com.warehouse.mapper.DamageRecordMapper;
import com.warehouse.service.DamageRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DamageRecordServiceImpl implements DamageRecordService {

    private final DamageRecordMapper damageRecordMapper;

    @Override
    public Page<DamageRecord> page(int current, int size, String status, Long warehouseId) {
        return damageRecordMapper.selectWithNames(new Page<>(current, size), status, warehouseId);
    }

    @Override
    public Long create(DamageRecordDTO dto, String createdBy) {
        DamageRecord record = new DamageRecord();
        record.setWarehouseId(dto.getWarehouseId());
        record.setProductId(dto.getProductId());
        record.setQty(dto.getQty());
        record.setStatus("PENDING");
        record.setRemark(dto.getRemark());
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(createdBy != null ? createdBy : "");
        damageRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public long countPendingAvailable(Long warehouseId) {
        return damageRecordMapper.countPendingAvailable(warehouseId);
    }

    @Override
    public List<DamageRecord> listPendingAvailable(Long warehouseId) {
        return damageRecordMapper.selectPendingAvailable(warehouseId);
    }

    @Override
    public void delete(Long id) {
        DamageRecord record = damageRecordMapper.selectById(id);
        if (record == null) throw new BusinessException("损坏记录不存在");
        if ("RESOLVED".equals(record.getStatus())) throw new BusinessException("已核销的记录不能删除");
        damageRecordMapper.deleteById(id);
    }
}
