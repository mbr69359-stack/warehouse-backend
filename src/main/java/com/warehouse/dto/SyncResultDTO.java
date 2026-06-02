package com.warehouse.dto;

import lombok.Data;

@Data
public class SyncResultDTO {
    private int index;
    private boolean success;
    private String rejectReason;

    public static SyncResultDTO ok(int index) {
        SyncResultDTO r = new SyncResultDTO();
        r.index = index;
        r.success = true;
        return r;
    }

    public static SyncResultDTO fail(int index, String reason) {
        SyncResultDTO r = new SyncResultDTO();
        r.index = index;
        r.success = false;
        r.rejectReason = reason;
        return r;
    }
}