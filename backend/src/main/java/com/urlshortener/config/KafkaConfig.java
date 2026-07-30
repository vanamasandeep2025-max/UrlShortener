package com.urlshortener.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Spring Boot's auto-configured listener container factory picks up this single
 * CommonErrorHandler bean automatically. Failed records are retried with a fixed delay
 * up to app.kafka.retry.max-attempts times; once exhausted, DeadLetterPublishingRecoverer
 * republishes the raw record onto the "dead-letter" topic (not Spring Kafka's default
 * "<topic>.DLT" naming - the spec calls for a single literal dead-letter topic).
 */
@Configuration
public class KafkaConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.dead-letter}") String deadLetterTopic,
            @Value("${app.kafka.retry.max-attempts}") int maxAttempts,
            @Value("${app.kafka.retry.backoff-interval-ms}") long backoffIntervalMs) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate, (record, exception) -> new TopicPartition(deadLetterTopic, -1));

        // FixedBackOff's second arg is the number of RETRIES after the initial attempt.
        FixedBackOff backOff = new FixedBackOff(backoffIntervalMs, Math.max(maxAttempts - 1, 0));
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
