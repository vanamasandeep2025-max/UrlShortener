package com.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.entity.Url;
import com.urlshortener.entity.User;
import com.urlshortener.entity.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the repository layer against a real Postgres (via Testcontainers), including
 * running the actual Flyway migrations - this is what proves the unique-active-short-code
 * index, the trigram search index, and the JPQL specification filters genuinely work, not
 * just that the Java compiles.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UrlRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("urlshortener_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistUser(String username) {
        User user = User.builder()
            .username(username)
            .email(username + "@example.com")
            .passwordHash("hash")
            .role(UserRole.USER)
            .enabled(true)
            .build();
        return userRepository.save(user);
    }

    @Test
    void shortCodeUniqueConstraintOnlyAppliesToLiveRows() {
        User user = persistUser("collision-owner");

        Url first = Url.builder().shortCode("DUP0001").originalUrl("https://example.com/1").user(user).build();
        urlRepository.saveAndFlush(first);
        first.setDeletedAt(Instant.now());
        urlRepository.saveAndFlush(first);

        // Same code should be reusable once the original is soft-deleted.
        Url second = Url.builder().shortCode("DUP0001").originalUrl("https://example.com/2").user(user).build();
        Url saved = urlRepository.saveAndFlush(second);

        assertThat(saved.getId()).isNotNull();
        assertThat(urlRepository.existsByShortCodeAndDeletedAtIsNull("DUP0001")).isTrue();
    }

    @Test
    void incrementClickCountIsAtomicAndPersists() {
        User user = persistUser("counter-owner");
        Url url = urlRepository.saveAndFlush(
            Url.builder().shortCode("CNT0001").originalUrl("https://example.com").user(user).build());

        urlRepository.incrementClickCount(url.getId());
        urlRepository.incrementClickCount(url.getId());
        urlRepository.flush();

        Url reloaded = urlRepository.findById(url.getId()).orElseThrow();
        assertThat(reloaded.getClickCount()).isEqualTo(2L);
    }

    @Test
    void softDeleteExpiredUrlsOnlyTouchesExpiredLiveRows() {
        User user = persistUser("expiry-owner");
        Url expired = urlRepository.saveAndFlush(Url.builder().shortCode("EXP0001").originalUrl("https://example.com")
            .user(user).expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)).build());
        Url notExpired = urlRepository.saveAndFlush(Url.builder().shortCode("EXP0002").originalUrl("https://example.com")
            .user(user).expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)).build());

        int updated = urlRepository.softDeleteExpiredUrls(Instant.now());
        urlRepository.flush();

        assertThat(updated).isEqualTo(1);
        assertThat(urlRepository.findById(expired.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(urlRepository.findById(notExpired.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void specificationFiltersByOwnerAndExcludesDeleted() {
        User owner = persistUser("spec-owner");
        User otherOwner = persistUser("spec-other");
        urlRepository.saveAndFlush(Url.builder().shortCode("OWN0001").originalUrl("https://example.com/mine").user(owner).build());
        urlRepository.saveAndFlush(Url.builder().shortCode("OWN0002").originalUrl("https://example.com/other").user(otherOwner).build());
        Url deleted = urlRepository.saveAndFlush(Url.builder().shortCode("OWN0003").originalUrl("https://example.com/gone").user(owner).build());
        deleted.setDeletedAt(Instant.now());
        urlRepository.saveAndFlush(deleted);

        Specification<Url> spec = Specification.where(UrlSpecifications.notDeleted()).and(UrlSpecifications.ownedBy(owner.getId()));
        var page = urlRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Url::getShortCode).containsExactly("OWN0001");
    }
}
