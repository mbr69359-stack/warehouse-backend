package com.warehouse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.common.Result;
import com.warehouse.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 60;

    // 原子地 INCR + 仅在首次设置 EXPIRE，避免非原子操作导致 key 永不过期
    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1])" +
            " if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end" +
            " return c",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/auth/login".equals(request.getServletPath())) {
            try {
                String ip = getClientIp(request);
                String key = "login:ratelimit:" + ip;
                Long count = redisTemplate.execute(INCR_SCRIPT,
                        Collections.singletonList(key),
                        String.valueOf(WINDOW_SECONDS));
                if (count != null && count > MAX_ATTEMPTS) {
                    response.setStatus(429);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write(objectMapper.writeValueAsString(
                            Result.fail(ResultCode.TOO_MANY_REQUESTS)));
                    return;
                }
            } catch (Exception e) {
                // Redis 不可用时放行，限流失效但不影响登录
            }
        }
        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.trim().isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.trim().isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        int commaIdx = ip.indexOf(',');
        return commaIdx > 0 ? ip.substring(0, commaIdx).trim() : ip;
    }
}