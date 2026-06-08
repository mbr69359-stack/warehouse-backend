package com.warehouse.controller;

import com.warehouse.common.Result;
import com.warehouse.entity.SysConfig;
import com.warehouse.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    @GetMapping
    public Result<List<SysConfig>> list() {
        return Result.success(sysConfigService.list());
    }

    @PutMapping("/{key}")
    public Result<Void> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        sysConfigService.update(key, body.get("value"));
        return Result.success(null);
    }
}