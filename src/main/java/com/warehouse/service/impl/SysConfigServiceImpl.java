package com.warehouse.service.impl;

import com.warehouse.common.BusinessException;
import com.warehouse.entity.SysConfig;
import com.warehouse.mapper.SysConfigMapper;
import com.warehouse.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    @Override
    public List<SysConfig> list() {
        return sysConfigMapper.selectList(null);
    }

    @Override
    public void update(String key, String value) {
        SysConfig cfg = sysConfigMapper.selectById(key);
        if (cfg == null) throw new BusinessException("配置项不存在：" + key);
        cfg.setValue(value);
        sysConfigMapper.updateById(cfg);
    }

    @Override
    public BigDecimal getDecimal(String key, BigDecimal defaultVal) {
        SysConfig cfg = sysConfigMapper.selectById(key);
        if (cfg == null || cfg.getValue() == null) return defaultVal;
        try {
            return new BigDecimal(cfg.getValue());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}