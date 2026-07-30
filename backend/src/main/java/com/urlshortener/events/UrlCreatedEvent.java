package com.urlshortener.events;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published both as a Spring application event (so in-process listeners run only after
 * the creating transaction commits) and, verbatim, as the payload of the Kafka
 * "url-created" topic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlCreatedEvent {

    private UUID eventId;
    private UUID urlId;
    private String shortCode;
    private String originalUrl;
    private UUID userId;
    private Instant occurredAt;
    private String correlationId;
}
