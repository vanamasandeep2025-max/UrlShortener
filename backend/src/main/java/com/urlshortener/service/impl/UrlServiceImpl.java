package com.urlshortener.service.impl;

import com.urlshortener.audit.AuditService;
import com.urlshortener.dto.request.CreateUrlRequest;
import com.urlshortener.dto.request.UpdateExpiryRequest;
import com.urlshortener.dto.response.AnalyticsResponse;
import com.urlshortener.dto.response.PageResponse;
import com.urlshortener.dto.response.UrlRedirectTarget;
import com.urlshortener.dto.response.UrlResponse;
import com.urlshortener.entity.ActorType;
import com.urlshortener.entity.Url;
import com.urlshortener.entity.User;
import com.urlshortener.events.UrlCreatedEvent;
import com.urlshortener.exception.ResourceNotFoundException;
import com.urlshortener.mapper.UrlMapper;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.repository.UrlSpecifications;
import com.urlshortener.repository.UserRepository;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.service.UrlService;
import com.urlshortener.util.shortcode.ShortCodeGenerator;
import com.urlshortener.util.shortcode.ShortCodeGeneratorFactory;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final UrlMapper urlMapper;
    private final ShortCodeGeneratorFactory shortCodeGeneratorFactory;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AnalyticsService analyticsService;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.short-code.length}")
    private int shortCodeLength;

    @Value("${app.short-code.max-generation-attempts}")
    private int maxGenerationAttempts;

    @Override
    @Transactional
    public UrlResponse createUrl(CreateUrlRequest request, UUID currentUserId) {
        User user = userRepository.findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));

        String shortCode = generateUniqueShortCode(request.getUrl());

        Url url = Url.builder()
            .shortCode(shortCode)
            .originalUrl(request.getUrl())
            .user(user)
            .expiresAt(request.getExpiryDate())
            .passwordHash(StringUtils.hasText(request.getPassword()) ? passwordEncoder.encode(request.getPassword()) : null)
            .build();
        url = urlRepository.save(url);

        publishUrlCreatedEvent(url, user.getId());

        auditService.log(ActorType.USER, user.getId(), "URL_CREATED", "URL", url.getId().toString(),
            Map.of("shortCode", url.getShortCode(), "hasExpiry", url.getExpiresAt() != null), null);
        log.info("URL created: shortCode={} userId={} hasExpiry={} passwordProtected={}",
            url.getShortCode(), user.getId(), url.getExpiresAt() != null, url.getPasswordHash() != null);

        return toResponseWithShortUrl(url);
    }

    private void publishUrlCreatedEvent(Url url, UUID userId) {
        UrlCreatedEvent event = UrlCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .urlId(url.getId())
            .shortCode(url.getShortCode())
            .originalUrl(url.getOriginalUrl())
            .userId(userId)
            .occurredAt(Instant.now())
            .correlationId(MDC.get("requestId"))
            .build();
        eventPublisher.publishEvent(event);
    }

    private String generateUniqueShortCode(String originalUrl) {
        ShortCodeGenerator generator = shortCodeGeneratorFactory.getGenerator();
        for (int attempt = 0; attempt < maxGenerationAttempts; attempt++) {
            String candidate = generator.generate(shortCodeLength, originalUrl, attempt);
            if (!urlRepository.existsByShortCodeAndDeletedAtIsNull(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
            "Unable to generate a unique short code after " + maxGenerationAttempts + " attempts");
    }

    @Override
    @Cacheable(cacheNames = "urlLookup", key = "#shortCode")
    @Transactional(readOnly = true)
    public UrlRedirectTarget resolveForRedirect(String shortCode) {
        // @Cacheable short-circuits this body entirely on a cache hit, so this log line
        // only fires on a genuine cache miss (a fresh Postgres lookup) - by design, not an
        // omission: logging every cache-hit redirect would put a log line on the hot path
        // this whole cache exists to keep fast.
        Url url = urlRepository.findByShortCodeAndDeletedAtIsNull(shortCode)
            .orElseThrow(() -> {
                log.warn("Redirect lookup miss: shortCode={} (no such active link)", shortCode);
                return new ResourceNotFoundException("No URL found for code: " + shortCode);
            });
        log.info("Redirect resolved (cache miss): shortCode={} urlId={}", shortCode, url.getId());
        return new UrlRedirectTarget(url.getId(), url.getShortCode(), url.getOriginalUrl(), url.getPasswordHash(), url.getExpiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UrlResponse> listUrls(UUID currentUserId, boolean isAdmin, String search, String status, Pageable pageable) {
        Specification<Url> spec = Specification.where(UrlSpecifications.notDeleted());
        if (!isAdmin) {
            spec = spec.and(UrlSpecifications.ownedBy(currentUserId));
        }
        if (StringUtils.hasText(search)) {
            spec = spec.and(UrlSpecifications.searchOriginalUrlOrCode(search));
        }
        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            Specification<Url> statusSpec = UrlSpecifications.statusIs(status);
            if (statusSpec != null) {
                spec = spec.and(statusSpec);
            }
        }

        Page<Url> page = urlRepository.findAll(spec, pageable);
        Page<UrlResponse> mapped = page.map(this::toResponseWithShortUrl);
        return PageResponse.from(mapped);
    }

    @Override
    @Transactional
    public void softDelete(UUID id, UUID currentUserId, boolean isAdmin) {
        Url url = loadOwned(id, currentUserId, isAdmin);
        url.setDeletedAt(Instant.now());
        urlRepository.save(url);
        evictCaches(url.getShortCode());
        auditService.log(ActorType.USER, currentUserId, "URL_DELETED", "URL",
            url.getId().toString(), Map.of("shortCode", url.getShortCode()), null);
        log.info("URL soft-deleted: shortCode={} urlId={} byUserId={}", url.getShortCode(), url.getId(), currentUserId);
    }

    @Override
    @Transactional
    public UrlResponse updateExpiry(UUID id, UpdateExpiryRequest request, UUID currentUserId, boolean isAdmin) {
        Url url = loadOwned(id, currentUserId, isAdmin);
        url.setExpiresAt(request.getExpiresAt());
        url = urlRepository.save(url);
        evictCaches(url.getShortCode());
        auditService.log(ActorType.USER, currentUserId, "URL_EXPIRY_UPDATED", "URL", url.getId().toString(),
            Map.of("expiresAt", String.valueOf(request.getExpiresAt())), null);
        log.info("URL expiry updated: shortCode={} urlId={} newExpiresAt={}", url.getShortCode(), url.getId(), request.getExpiresAt());
        return toResponseWithShortUrl(url);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> verifyPasswordAndGetDestination(String shortCode, String rawPassword) {
        Url url = urlRepository.findByShortCodeAndDeletedAtIsNull(shortCode)
            .orElseThrow(() -> new ResourceNotFoundException("No URL found for code: " + shortCode));
        if (!url.isPasswordProtected()) {
            return Optional.of(url.getOriginalUrl());
        }
        if (passwordEncoder.matches(rawPassword, url.getPasswordHash())) {
            return Optional.of(url.getOriginalUrl());
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String shortCode, UUID currentUserId, boolean isAdmin) {
        Url url = urlRepository.findByShortCodeAndDeletedAtIsNull(shortCode)
            .orElseThrow(() -> new ResourceNotFoundException("No URL found for code: " + shortCode));
        if (!isAdmin && !url.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have access to this URL's analytics");
        }
        return analyticsService.buildAnalytics(url.getId(), url.getShortCode());
    }

    private Url loadOwned(UUID id, UUID currentUserId, boolean isAdmin) {
        Url url = urlRepository.findById(id)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException("URL not found: " + id));
        if (!isAdmin && !url.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have access to this URL");
        }
        return url;
    }

    private void evictCaches(String shortCode) {
        Optional.ofNullable(cacheManager.getCache("urlLookup")).ifPresent(c -> c.evict(shortCode));
        Optional.ofNullable(cacheManager.getCache("analytics")).ifPresent(c -> c.evict(shortCode));
    }

    private UrlResponse toResponseWithShortUrl(Url url) {
        UrlResponse response = urlMapper.toResponse(url);
        response.setShortUrl(baseUrl + "/" + url.getShortCode());
        return response;
    }
}
