package com.urlshortener.metrics;

import com.urlshortener.events.UrlClickedEvent;
import com.urlshortener.events.UrlCreatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Business-level counters exposed alongside Spring Boot's automatic HTTP/JVM/Hikari/Kafka
 * metrics at /actuator/prometheus. Hooked off the same domain events the Kafka bridge
 * uses (see producer/DomainEventKafkaBridge) rather than called directly from the
 * service layer, so instrumentation stays out of business logic.
 */
@Component
@RequiredArgsConstructor
public class DomainMetrics {

    private final MeterRegistry meterRegistry;

    @EventListener
    public void onUrlCreated(UrlCreatedEvent event) {
        meterRegistry.counter("urlshortener.urls.created.total").increment();
    }

    @EventListener
    public void onUrlClicked(UrlClickedEvent event) {
        meterRegistry.counter("urlshortener.urls.clicked.total").increment();
    }
}
