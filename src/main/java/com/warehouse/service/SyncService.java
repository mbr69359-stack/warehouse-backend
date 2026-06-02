package com.warehouse.service;

import com.warehouse.dto.SyncItemDTO;
import com.warehouse.dto.SyncResultDTO;
import java.util.List;

public interface SyncService {
    List<SyncResultDTO> batchSync(List<SyncItemDTO> items);
}