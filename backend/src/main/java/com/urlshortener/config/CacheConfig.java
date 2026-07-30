package com.urlshortener.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Per-cache TTL/eviction policy on top of Spring Boot's auto-configured RedisCacheManager.
 * "urlLookup" (redirect hot path) and "analytics" get their own TTLs from app config instead
 * of sharing the global spring.cache.redis.time-to-live default.
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer(
            @Value("${app.cache.url-lookup-ttl-seconds}") long urlLookupTtlSeconds,
            @Value("${app.cache.analytics-ttl-seconds}") long analyticsTtlSeconds) {
        return builder -> {
            Map<String, RedisCacheConfiguration> configs = new HashMap<>();
            configs.put("urlLookup", baseConfig().entryTtl(Duration.ofSeconds(urlLookupTtlSeconds)));
            configs.put("analytics", baseConfig().entryTtl(Duration.ofSeconds(analyticsTtlSeconds)));
            builder.withInitialCacheConfigurations(configs);
        };
    }

    private RedisCacheConfiguration baseConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();
    }
}
