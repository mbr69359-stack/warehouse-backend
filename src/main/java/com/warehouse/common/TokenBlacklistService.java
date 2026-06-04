package com.warehouse.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void revoke(String token, long expiryMs) {
        long ttlMs = expiryMs - System.currentTimeMillis();
        if (ttlMs <= 0) return;
        redisTemplate.opsForValue().set(PREFIX + token, "1", ttlMs, TimeUnit.MILLISECONDS);
    }

    public boolean isRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}