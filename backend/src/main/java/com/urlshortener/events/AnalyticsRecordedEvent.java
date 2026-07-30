package com.urlshortener.events;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published to the "analytics" topic by the click consumer once a raw url-clicked event
 * has been enriched (browser/OS/device parsed, geo resolved) and persisted. Represents
 * the fan-out point for downstream analytics/BI consumers outside this service's boundary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsRecordedEvent {

    private UUID eventId;
    private UUID urlId;
    private String shortCode;
    private String browser;
    private String os;
    private String deviceType;
    private String country;
    private Instant occurredAt;
}
