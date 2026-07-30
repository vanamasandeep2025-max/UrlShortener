package com.urlshortener.producer;

import com.urlshortener.events.UrlClickedEvent;
import com.urlshortener.events.UrlCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer-pattern seam between the service layer and Kafka: services publish plain
 * in-process domain events via ApplicationEventPublisher and never touch KafkaTemplate
 * directly. UrlCreatedEvent is only forwarded to Kafka AFTER_COMMIT, so a rolled-back
 * URL creation never produces a phantom "url-created" message. UrlClickedEvent has no
 * surrounding transaction (the redirect path deliberately does no synchronous DB write),
 * so it's handled by a plain listener instead.
 */
@Component
@RequiredArgsConstructor
public class DomainEventKafkaBridge {

    private final KafkaEventPublisher kafkaEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUrlCreated(UrlCreatedEvent event) {
        kafkaEventPublisher.publishUrlCreated(event);
    }

    @EventListener
    public void onUrlClicked(UrlClickedEvent event) {
        kafkaEventPublisher.publishUrlClicked(event);
    }
}
