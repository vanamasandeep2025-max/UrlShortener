package com.urlshortener.cache;

public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
}
