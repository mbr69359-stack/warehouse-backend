package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.dto.SyncItemDTO;
import com.warehouse.dto.SyncResultDTO;
import com.warehouse.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
public class SyncController {
    private final SyncService syncService;

    @PostMapping("/batch")
    public Result<List<SyncResultDTO>> batch(@RequestBody List<SyncItemDTO> items) {
        return Result.success(syncService.batchSync(items));
    }
}