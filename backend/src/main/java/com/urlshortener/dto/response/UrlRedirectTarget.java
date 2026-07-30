package com.urlshortener.dto.response;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight, cache-safe projection used on the hot redirect path - deliberately not
 * the JPA entity, so what gets serialized into Redis has no lazy associations or
 * persistence-context baggage. expiresAt travels with it so expiry is re-evaluated at
 * read time even when this value came from cache rather than a fresh DB read.
 */
public record UrlRedirectTarget(
    UUID urlId,
    String shortCode,
    String originalUrl,
    String passwordHash,
    Instant expiresAt
) implements Serializable {

    public boolean isPasswordProtected() {
        return passwordHash != null;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
