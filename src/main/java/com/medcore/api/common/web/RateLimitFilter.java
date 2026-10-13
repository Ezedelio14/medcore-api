package com.medcore.api.common.web;

import com.medcore.api.common.exception.GlobalExceptionHandler;
import com.medcore.api.config.MedcoreProperties;
import com.medcore.api.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window rate limiter backed by Redis, so limits hold across all API instances.
 * Authenticated callers are limited per user, anonymous callers per IP; auth endpoints get a stricter budget
 * to slow down credential stuffing. Fails open if Redis is unavailable.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "medcore:ratelimit:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final MedcoreProperties.RateLimit config;

    public RateLimitFilter(StringRedisTemplate redis, JsonMapper jsonMapper, MedcoreProperties properties) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.config = properties.rateLimit();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !config.enabled() || !path.startsWith("/api/") || path.startsWith("/api/v1/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean authEndpoint = request.getRequestURI().startsWith("/api/v1/auth/");
        int limit = authEndpoint ? config.authRequestsPerMinute() : config.requestsPerMinute();
        String subject = CurrentUser.find().map(u -> "user:" + u.id()).orElse("ip:" + request.getRemoteAddr());
        long window = Instant.now().getEpochSecond() / WINDOW.toSeconds();
        String key = KEY_PREFIX + (authEndpoint ? "auth:" : "api:") + subject + ":" + window;

        Long count = increment(key);
        if (count != null) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));
            if (count > limit) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                jsonMapper.writeValue(response.getOutputStream(), GlobalExceptionHandler.problem(
                        HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests, please retry later"));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Long increment(String key) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
            return count;
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            return null;
        }
    }
}
