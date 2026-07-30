package com.urlshortener.producer;

import com.urlshortener.events.AnalyticsRecordedEvent;
import com.urlshortener.events.UrlClickedEvent;
import com.urlshortener.events.UrlCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Thin wrapper around KafkaTemplate: picks the right topic/key per event type and logs send outcomes. */
@Slf4j
@Component
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String urlCreatedTopic;
    private final String urlClickedTopic;
    private final String analyticsTopic;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                @Value("${app.kafka.topics.url-created}") String urlCreatedTopic,
                                @Value("${app.kafka.topics.url-clicked}") String urlClickedTopic,
                                @Value("${app.kafka.topics.analytics}") String analyticsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.urlCreatedTopic = urlCreatedTopic;
        this.urlClickedTopic = urlClickedTopic;
        this.analyticsTopic = analyticsTopic;
    }

    public void publishUrlCreated(UrlCreatedEvent event) {
        send(urlCreatedTopic, event.getShortCode(), event);
    }

    public void publishUrlClicked(UrlClickedEvent event) {
        send(urlClickedTopic, event.getShortCode(), event);
    }

    public void publishAnalyticsRecorded(AnalyticsRecordedEvent event) {
        send(analyticsTopic, event.getShortCode(), event);
    }

    private void send(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to topic {} (key={}): {}", topic, key, ex.getMessage(), ex);
            } else {
                log.debug("Published to {}-{} offset={}", topic,
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
