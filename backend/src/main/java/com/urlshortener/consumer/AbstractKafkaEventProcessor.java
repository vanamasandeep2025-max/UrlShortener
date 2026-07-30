package com.urlshortener.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Template Method: fixes the shape of "consume one Kafka record" (log receipt, process,
 * acknowledge on success) while leaving the actual processing to subclasses. Acknowledgment
 * only happens on the success path - if process() throws, the exception propagates to the
 * listener container's configured CommonErrorHandler (retry with backoff, then DLQ; see
 * config/KafkaConfig), which manages the offset itself.
 */
@Slf4j
public abstract class AbstractKafkaEventProcessor<T> {

    protected final void handle(T event, Acknowledgment acknowledgment) {
        log.debug("Processing {} event: {}", eventName(), event);
        boolean applied = process(event);
        if (!applied) {
            log.info("Skipped duplicate {} event: {}", eventName(), event);
        }
        acknowledgment.acknowledge();
    }

    /** @return true if the event was newly applied; false if it was recognized as a duplicate and skipped. */
    protected abstract boolean process(T event);

    protected abstract String eventName();
}
