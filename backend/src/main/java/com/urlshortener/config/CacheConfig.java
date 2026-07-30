package com.urlshortener.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.response.AnalyticsResponse;
import com.urlshortener.dto.response.UrlRedirectTarget;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Per-cache TTL/eviction policy on top of Spring Boot's auto-configured RedisCacheManager.
 * "urlLookup" (redirect hot path) and "analytics" get their own TTLs from app config instead
 * of sharing the global spring.cache.redis.time-to-live default.
 */
@Configuration
public class CacheConfig {

    private final ObjectMapper objectMapper;

    public CacheConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer(
            @Value("${app.cache.url-lookup-ttl-seconds}") long urlLookupTtlSeconds,
            @Value("${app.cache.analytics-ttl-seconds}") long analyticsTtlSeconds) {
        return builder -> {
            Map<String, RedisCacheConfiguration> configs = new HashMap<>();
            configs.put("urlLookup", baseConfig(UrlRedirectTarget.class).entryTtl(Duration.ofSeconds(urlLookupTtlSeconds)));
            configs.put("analytics", baseConfig(AnalyticsResponse.class).entryTtl(Duration.ofSeconds(analyticsTtlSeconds)));
            builder.withInitialCacheConfigurations(configs);
        };
    }

    // GenericJackson2JsonRedisSerializer relies on embedded "@class" type hints to know what
    // to deserialize a cached JSON blob back into. Its no-arg constructor activates that
    // polymorphic typing internally, but the no-arg mapper also lacks JavaTimeModule, so it
    // blows up serializing java.time fields (e.g. AnalyticsResponse.dailyClicks[].date).
    // Handing it a copy of the app's real ObjectMapper fixes the serialize side (JavaTimeModule
    // is present) but does NOT activate default-typing on that copy, so the cached JSON carries
    // no "@class" hint - on read, Jackson falls back to a plain LinkedHashMap instead of the
    // concrete DTO, which Spring's cache layer then fails to cast (ClassCastException).
    //
    // Since each cache here only ever holds one concrete, known type, the type hint isn't
    // needed at all: Jackson2JsonRedisSerializer<T> is constructed with that target type up
    // front, so every read deserializes straight into T without any "@class" metadata.
    private <T> RedisCacheConfiguration baseConfig(Class<T> type) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(objectMapper, type)))
            .disableCachingNullValues();
    }
}
