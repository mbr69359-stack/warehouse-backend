package com.warehouse.dto;

import lombok.Data;

@Data
public class SyncResultDTO {
    private int index;
    private String clientId;
    private Long localId;
    private boolean success;
    private String rejectReason;

    public static SyncResultDTO ok(int index) {
        return ok(index, null, null);
    }

    public static SyncResultDTO ok(int index, Long localId) {
        return ok(index, null, localId);
    }

    public static SyncResultDTO ok(int index, String clientId, Long localId) {
        SyncResultDTO r = new SyncResultDTO();
        r.index = index;
        r.clientId = clientId;
        r.localId = localId;
        r.success = true;
        return r;
    }

    public static SyncResultDTO fail(int index, String reason) {
        return fail(index, null, reason);
    }

    public static SyncResultDTO fail(int index, Long localId, String reason) {
        return fail(index, null, localId, reason);
    }

    public static SyncResultDTO fail(int index, String clientId, Long localId, String reason) {
        SyncResultDTO r = new SyncResultDTO();
        r.index = index;
        r.clientId = clientId;
        r.localId = localId;
        r.success = false;
        r.rejectReason = reason;
        return r;
    }
}
