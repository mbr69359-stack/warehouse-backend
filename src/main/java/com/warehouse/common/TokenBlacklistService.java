package com.warehouse.common;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenBlacklistService {

    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    public void revoke(String token, long expiryMs) {
        if (expiryMs > System.currentTimeMillis()) {
            blacklist.put(token, expiryMs);
        }
    }

    public boolean isRevoked(String token) {
        Long exp = blacklist.get(token);
        if (exp == null) return false;
        if (System.currentTimeMillis() >= exp) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    // clean up expired entries every hour
    @Scheduled(fixedDelay = 3_600_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(e -> now >= e.getValue());
    }
}