package com.warehouse.config;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenStore {
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    public void save(String username, String token) {
        store.put(username, token);
    }

    public boolean isValid(String username, String token) {
        return token.equals(store.get(username));
    }

    public void remove(String username) {
        store.remove(username);
    }
}