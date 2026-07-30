package com.urlshortener.events;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by the redirect endpoint (fire-and-forget, off the request's critical path)
 * and consumed by the analytics consumer to enrich + persist a UrlClick row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlClickedEvent {

    private UUID eventId;
    private UUID urlId;
    private String shortCode;
    private String ipAddress;
    private String userAgent;
    private String referrer;
    private Instant occurredAt;
    private String correlationId;
}
