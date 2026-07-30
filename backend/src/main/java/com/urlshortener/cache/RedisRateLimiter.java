package com.urlshortener.cache;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Distributed fixed-window rate limiter backed by Redis, so the limit is shared correctly
 * across every stateless backend instance rather than per-instance. The INCR+EXPIRE check
 * runs as a single Lua script (scripts/rate_limiter.lua) so the read-check-increment is
 * atomic even under concurrent requests for the same key.
 */
@Slf4j
@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> script;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = RedisScript.of(new ClassPathResource("scripts/rate_limiter.lua"), List.class);
    }

    @SuppressWarnings("unchecked")
    public RateLimitResult tryConsume(String key, int capacity, int windowSeconds) {
        try {
            List<Long> result = redisTemplate.execute(
                script, List.of(key), String.valueOf(capacity), String.valueOf(windowSeconds));
            if (result == null || result.size() < 2) {
                return new RateLimitResult(true, -1);
            }
            return new RateLimitResult(result.get(0) == 1L, result.get(1));
        } catch (RedisConnectionFailureException e) {
            // Fail open: Redis being down must not take the whole API down over rate limiting.
            log.warn("Rate limiter Redis call failed, failing open for key={}: {}", key, e.getMessage());
            return new RateLimitResult(true, -1);
        }
    }
}
