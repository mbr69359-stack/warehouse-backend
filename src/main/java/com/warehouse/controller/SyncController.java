package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.dto.SyncItemDTO;
import com.warehouse.dto.SyncResultDTO;
import com.warehouse.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;
import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SyncController {
    private final SyncService syncService;

    @PostMapping("/batch")
    public Result<List<SyncResultDTO>> batch(@RequestBody @Valid List<SyncItemDTO> items) {
        return Result.success(syncService.batchSync(items));
    }
}