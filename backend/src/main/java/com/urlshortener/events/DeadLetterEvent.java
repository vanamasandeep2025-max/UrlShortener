package com.urlshortener.events;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Envelope written to the "dead-letter" topic once a message exhausts its retry budget. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterEvent {

    private String originalTopic;
    private int originalPartition;
    private long originalOffset;
    private String payload;
    private String exceptionType;
    private String exceptionMessage;
    private int attempts;
    private Instant failedAt;
}
