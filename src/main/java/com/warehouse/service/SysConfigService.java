package com.warehouse.service;

import com.warehouse.entity.SysConfig;
import java.math.BigDecimal;
import java.util.List;

public interface SysConfigService {
    List<SysConfig> list();
    void update(String key, String value);
    BigDecimal getDecimal(String key, BigDecimal defaultVal);
}