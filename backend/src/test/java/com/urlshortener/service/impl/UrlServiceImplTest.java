package com.urlshortener.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urlshortener.audit.AuditService;
import com.urlshortener.dto.request.CreateUrlRequest;
import com.urlshortener.dto.request.UpdateExpiryRequest;
import com.urlshortener.dto.response.UrlRedirectTarget;
import com.urlshortener.dto.response.UrlResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.entity.User;
import com.urlshortener.entity.UserRole;
import com.urlshortener.events.UrlCreatedEvent;
import com.urlshortener.exception.ResourceNotFoundException;
import com.urlshortener.mapper.UrlMapper;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.repository.UserRepository;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.util.shortcode.ShortCodeGenerator;
import com.urlshortener.util.shortcode.ShortCodeGeneratorFactory;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UrlServiceImplTest {

    @Mock private UrlRepository urlRepository;
    @Mock private UserRepository userRepository;
    @Mock private UrlMapper urlMapper;
    @Mock private ShortCodeGeneratorFactory shortCodeGeneratorFactory;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private AnalyticsService analyticsService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CacheManager cacheManager;

    private UrlServiceImpl urlService;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        urlService = new UrlServiceImpl(urlRepository, userRepository, urlMapper, shortCodeGeneratorFactory,
            passwordEncoder, auditService, analyticsService, eventPublisher, cacheManager);
        ReflectionTestUtils.setField(urlService, "baseUrl", "https://short.test");
        ReflectionTestUtils.setField(urlService, "shortCodeLength", 7);
        ReflectionTestUtils.setField(urlService, "maxGenerationAttempts", 5);
    }

    private User owner() {
        return User.builder().id(ownerId).username("owner").email("owner@example.com")
            .passwordHash("hash").role(UserRole.USER).enabled(true).build();
    }

    @Test
    void createUrlPersistsAndReturnsShortUrlBuiltFromBaseUrl() {
        User user = owner();
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(user));

        ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
        when(generator.generate(eq(7), anyString(), anyInt())).thenReturn("ABC1234");
        when(shortCodeGeneratorFactory.getGenerator()).thenReturn(generator);
        when(urlRepository.existsByShortCodeAndDeletedAtIsNull("ABC1234")).thenReturn(false);

        Url savedUrl = Url.builder().id(UUID.randomUUID()).shortCode("ABC1234")
            .originalUrl("https://example.com").user(user).clickCount(0).createdAt(Instant.now())
            .updatedAt(Instant.now()).build();
        when(urlRepository.save(any(Url.class))).thenReturn(savedUrl);

        UrlResponse mappedResponse = UrlResponse.builder().shortCode("ABC1234").originalUrl("https://example.com").build();
        when(urlMapper.toResponse(savedUrl)).thenReturn(mappedResponse);

        CreateUrlRequest request = CreateUrlRequest.builder().url("https://example.com").build();

        UrlResponse response = urlService.createUrl(request, ownerId);

        assertThat(response.getShortUrl()).isEqualTo("https://short.test/ABC1234");
        verify(eventPublisher).publishEvent(any(UrlCreatedEvent.class));
        verify(auditService).log(any(), eq(ownerId), eq("URL_CREATED"), eq("URL"), anyString(), any(), isNull());
    }

    @Test
    void createUrlRetriesOnShortCodeCollision() {
        User user = owner();
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(user));

        ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
        when(generator.generate(eq(7), anyString(), eq(0))).thenReturn("TAKEN01");
        when(generator.generate(eq(7), anyString(), eq(1))).thenReturn("FREE001");
        when(shortCodeGeneratorFactory.getGenerator()).thenReturn(generator);
        when(urlRepository.existsByShortCodeAndDeletedAtIsNull("TAKEN01")).thenReturn(true);
        when(urlRepository.existsByShortCodeAndDeletedAtIsNull("FREE001")).thenReturn(false);

        Url savedUrl = Url.builder().id(UUID.randomUUID()).shortCode("FREE001")
            .originalUrl("https://example.com").user(user).build();
        when(urlRepository.save(any(Url.class))).thenReturn(savedUrl);
        when(urlMapper.toResponse(savedUrl)).thenReturn(UrlResponse.builder().shortCode("FREE001").build());

        CreateUrlRequest request = CreateUrlRequest.builder().url("https://example.com").build();

        UrlResponse response = urlService.createUrl(request, ownerId);

        assertThat(response.getShortCode()).isEqualTo("FREE001");
        verify(generator, times(2)).generate(eq(7), anyString(), anyInt());
    }

    @Test
    void createUrlThrowsWhenGenerationAttemptsExhausted() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner()));
        ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
        when(generator.generate(anyInt(), anyString(), anyInt())).thenReturn("ALWAYS1");
        when(shortCodeGeneratorFactory.getGenerator()).thenReturn(generator);
        when(urlRepository.existsByShortCodeAndDeletedAtIsNull("ALWAYS1")).thenReturn(true);

        CreateUrlRequest request = CreateUrlRequest.builder().url("https://example.com").build();

        assertThatThrownBy(() -> urlService.createUrl(request, ownerId))
            .isInstanceOf(IllegalStateException.class);
        verify(urlRepository, never()).save(any());
    }

    @Test
    void resolveForRedirectThrowsNotFoundForUnknownCode() {
        when(urlRepository.findByShortCodeAndDeletedAtIsNull("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolveForRedirect("missing"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveForRedirectReturnsTargetCarryingExpiryForCallerToCheck() {
        Instant expiry = Instant.now().minusSeconds(60);
        Url url = Url.builder().id(UUID.randomUUID()).shortCode("EXP0001")
            .originalUrl("https://example.com").expiresAt(expiry).build();
        when(urlRepository.findByShortCodeAndDeletedAtIsNull("EXP0001")).thenReturn(Optional.of(url));

        UrlRedirectTarget target = urlService.resolveForRedirect("EXP0001");

        assertThat(target.isExpired()).isTrue();
    }

    @Test
    void softDeleteRejectsNonOwnerNonAdmin() {
        Url url = Url.builder().id(UUID.randomUUID()).shortCode("XYZ0001").user(owner()).build();
        when(urlRepository.findById(url.getId())).thenReturn(Optional.of(url));

        UUID intruder = UUID.randomUUID();

        assertThatThrownBy(() -> urlService.softDelete(url.getId(), intruder, false))
            .isInstanceOf(AccessDeniedException.class);
        verify(urlRepository, never()).save(any());
    }

    @Test
    void softDeleteAllowsAdminRegardlessOfOwnership() {
        Url url = Url.builder().id(UUID.randomUUID()).shortCode("XYZ0002").user(owner()).build();
        when(urlRepository.findById(url.getId())).thenReturn(Optional.of(url));
        when(urlRepository.save(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID admin = UUID.randomUUID();
        urlService.softDelete(url.getId(), admin, true);

        assertThat(url.getDeletedAt()).isNotNull();
        verify(urlRepository).save(url);
    }

    @Test
    void updateExpiryThrowsNotFoundForMissingUrl() {
        UUID id = UUID.randomUUID();
        when(urlRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.updateExpiry(id, UpdateExpiryRequest.builder().build(), ownerId, false))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyPasswordReturnsDestinationWhenNotProtected() {
        Url url = Url.builder().shortCode("PUB0001").originalUrl("https://example.com").build();
        when(urlRepository.findByShortCodeAndDeletedAtIsNull("PUB0001")).thenReturn(Optional.of(url));

        Optional<String> result = urlService.verifyPasswordAndGetDestination("PUB0001", "irrelevant");

        assertThat(result).contains("https://example.com");
    }

    @Test
    void verifyPasswordReturnsEmptyOnWrongPassword() {
        Url url = Url.builder().shortCode("PROT001").originalUrl("https://example.com").passwordHash("hashed").build();
        when(urlRepository.findByShortCodeAndDeletedAtIsNull("PROT001")).thenReturn(Optional.of(url));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        Optional<String> result = urlService.verifyPasswordAndGetDestination("PROT001", "wrong");

        assertThat(result).isEmpty();
    }

    @Test
    void verifyPasswordReturnsDestinationOnCorrectPassword() {
        Url url = Url.builder().shortCode("PROT002").originalUrl("https://example.com").passwordHash("hashed").build();
        when(urlRepository.findByShortCodeAndDeletedAtIsNull("PROT002")).thenReturn(Optional.of(url));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        Optional<String> result = urlService.verifyPasswordAndGetDestination("PROT002", "correct");

        assertThat(result).contains("https://example.com");
    }

    @Test
    void getAnalyticsRejectsNonOwnerNonAdmin() {
        Url url = Url.builder().id(UUID.randomUUID()).shortCode("AN00001").user(owner()).build();
        when(urlRepository.findByShortCodeAndDeletedAtIsNull("AN00001")).thenReturn(Optional.of(url));

        UUID intruder = UUID.randomUUID();

        assertThatThrownBy(() -> urlService.getAnalytics("AN00001", intruder, false))
            .isInstanceOf(AccessDeniedException.class);
    }
}
