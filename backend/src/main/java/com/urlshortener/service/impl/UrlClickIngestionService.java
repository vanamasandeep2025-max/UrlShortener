package com.urlshortener.service.impl;

import com.urlshortener.entity.UrlClick;
import com.urlshortener.events.UrlClickedEvent;
import com.urlshortener.repository.UrlClickRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.IpHashUtil;
import com.urlshortener.util.useragent.ParsedUserAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the transaction boundary for persisting a click. Deliberately a separate bean
 * from the @KafkaListener consumer (rather than a method on it): Spring's @Transactional
 * only takes effect on calls that go through the proxy, and a listener calling its own
 * annotated method internally would silently skip the transaction (self-invocation).
 * Calling out to this bean's proxy from the consumer sidesteps that pitfall.
 */
@Service
@RequiredArgsConstructor
public class UrlClickIngestionService {

    private final UrlClickRepository urlClickRepository;
    private final UrlRepository urlRepository;

    /** @return true if a new click row was recorded; false if this event_id was already processed (idempotent no-op). */
    @Transactional
    public boolean recordClick(UrlClickedEvent event, ParsedUserAgent parsed, String country) {
        if (urlClickRepository.existsByEventId(event.getEventId())) {
            return false;
        }

        UrlClick click = UrlClick.builder()
            .url(urlRepository.getReferenceById(event.getUrlId()))
            .eventId(event.getEventId())
            .clickedAt(event.getOccurredAt())
            .ipAddress(event.getIpAddress())
            .ipHash(IpHashUtil.hash(event.getIpAddress()))
            .userAgent(event.getUserAgent())
            .browser(parsed.browser())
            .browserVersion(parsed.browserVersion())
            .os(parsed.os())
            .osVersion(parsed.osVersion())
            .deviceType(parsed.deviceType())
            .country(country)
            .referrer(event.getReferrer())
            .correlationId(event.getCorrelationId())
            .build();
        urlClickRepository.save(click);
        urlRepository.incrementClickCount(event.getUrlId());
        return true;
    }
}
