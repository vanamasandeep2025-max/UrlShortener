package com.urlshortener.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisRateLimiter(redisTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void allowsWhenScriptReturnsAllowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
            .thenReturn(List.of(1L, -1L));

        RateLimitResult result = rateLimiter.tryConsume("k", 10, 60);

        assertThat(result.allowed()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void deniesAndReportsRetryAfterWhenScriptReturnsDenied() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
            .thenReturn(List.of(0L, 42L));

        RateLimitResult result = rateLimiter.tryConsume("k", 10, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(42L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void failsOpenWhenRedisIsUnreachable() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
            .thenThrow(new RedisConnectionFailureException("connection refused"));

        RateLimitResult result = rateLimiter.tryConsume("k", 10, 60);

        assertThat(result.allowed()).isTrue();
    }
}
