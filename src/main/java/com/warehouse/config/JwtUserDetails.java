package com.warehouse.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.Collection;

public class JwtUserDetails extends User {
    private final Long userId;

    public JwtUserDetails(Long userId, String username, String password,
                          Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
}