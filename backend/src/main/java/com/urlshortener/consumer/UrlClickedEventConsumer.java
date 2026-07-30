package com.urlshortener.consumer;

import com.urlshortener.events.AnalyticsRecordedEvent;
import com.urlshortener.events.UrlClickedEvent;
import com.urlshortener.producer.KafkaEventPublisher;
import com.urlshortener.service.impl.UrlClickIngestionService;
import com.urlshortener.util.geo.GeoIpService;
import com.urlshortener.util.useragent.ParsedUserAgent;
import com.urlshortener.util.useragent.UserAgentParsingService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes "url-clicked" (raw click events published off the redirect hot path),
 * enriches with UA-parsed browser/OS/device + geo country, persists via
 * UrlClickIngestionService, and fans out an enriched event to the "analytics" topic
 * for any downstream consumer. Idempotent: UrlClickIngestionService dedupes on event_id
 * before writing, backed by url_clicks.event_id's unique constraint as a hard backstop.
 */
@Component
@RequiredArgsConstructor
public class UrlClickedEventConsumer extends AbstractKafkaEventProcessor<UrlClickedEvent> {

    private final UrlClickIngestionService ingestionService;
    private final UserAgentParsingService userAgentParsingService;
    private final GeoIpService geoIpService;
    private final KafkaEventPublisher kafkaEventPublisher;

    @KafkaListener(topics = "${app.kafka.topics.url-clicked}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(UrlClickedEvent event, Acknowledgment acknowledgment) {
        handle(event, acknowledgment);
    }

    @Override
    protected boolean process(UrlClickedEvent event) {
        ParsedUserAgent parsed = userAgentParsingService.parse(event.getUserAgent());
        String country = geoIpService.lookupCountry(event.getIpAddress());

        boolean recorded = ingestionService.recordClick(event, parsed, country);
        if (recorded) {
            kafkaEventPublisher.publishAnalyticsRecorded(AnalyticsRecordedEvent.builder()
                .eventId(event.getEventId())
                .urlId(event.getUrlId())
                .shortCode(event.getShortCode())
                .browser(parsed.browser())
                .os(parsed.os())
                .deviceType(parsed.deviceType() != null ? parsed.deviceType().name() : null)
                .country(country)
                .occurredAt(Instant.now())
                .build());
        }
        return recorded;
    }

    @Override
    protected String eventName() {
        return "url-clicked";
    }
}
