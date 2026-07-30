package com.urlshortener.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.urlshortener.dto.request.CreateUrlRequest;
import com.urlshortener.dto.request.RegisterRequest;
import com.urlshortener.dto.response.AnalyticsResponse;
import com.urlshortener.dto.response.AuthResponse;
import com.urlshortener.dto.response.UrlResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end proof that the whole stack described in docs/ARCHITECTURE.md actually works
 * together: register -> create a short URL over the real HTTP+security stack -> hit the
 * real redirect endpoint -> the redirect's fire-and-forget Kafka event flows through the
 * real analytics consumer (UA parsing, idempotent persistence) into a real Postgres row,
 * observable via the analytics endpoint. This is deliberately the one test in the suite
 * that exercises every moving part at once; unit tests cover the individual pieces in
 * isolation elsewhere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class UrlShortenerFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("urlshortener_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.seed-demo-data", () -> "false");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    /**
     * A plain RestTemplate with redirect-following explicitly disabled at the JDK
     * HttpURLConnection level, used only for the one request where we must observe the
     * raw 302 rather than have it silently followed (which would otherwise fire a real
     * outbound HTTP call to example.com from inside the test).
     */
    private RestTemplate nonFollowingRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        return new RestTemplate(factory);
    }

    @Test
    void shortenRedirectAndAnalyticsFlowEndToEnd() {
        String username = "flowuser-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest registerRequest = RegisterRequest.builder()
            .username(username)
            .email(username + "@example.com")
            .password("Passw0rd!")
            .build();
        ResponseEntity<AuthResponse> registerResponse =
            restTemplate.postForEntity("/api/v1/auth/register", registerRequest, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accessToken = registerResponse.getBody().getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        CreateUrlRequest createRequest = CreateUrlRequest.builder().url("https://example.com/integration-flow").build();
        ResponseEntity<UrlResponse> createResponse = restTemplate.exchange(
            "/api/v1/urls", HttpMethod.POST, new HttpEntity<>(createRequest, headers), UrlResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String shortCode = createResponse.getBody().getShortCode();
        assertThat(shortCode).isNotBlank();

        // Redirects are explicitly not followed here, so this captures the raw 302 instead
        // of actually calling out to example.com.
        HttpHeaders clickHeaders = new HttpHeaders();
        clickHeaders.set(HttpHeaders.USER_AGENT,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        String redirectUrl = "http://localhost:" + port + "/" + shortCode;
        ResponseEntity<Void> redirectResponse = nonFollowingRestTemplate().exchange(
            redirectUrl, HttpMethod.GET, new HttpEntity<>(clickHeaders), Void.class);
        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResponse.getHeaders().getLocation()).hasToString("https://example.com/integration-flow");

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            ResponseEntity<AnalyticsResponse> analyticsResponse = restTemplate.exchange(
                "/api/v1/urls/" + shortCode + "/analytics", HttpMethod.GET, new HttpEntity<>(headers), AnalyticsResponse.class);
            assertThat(analyticsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(analyticsResponse.getBody().getTotalClicks()).isGreaterThanOrEqualTo(1L);
            assertThat(analyticsResponse.getBody().getBrowsers()).containsKey("Chrome");
        });
    }

    @Test
    void redirectReturns404ForUnknownShortCode() {
        ResponseEntity<String> response = restTemplate.getForEntity("/does-not-exist-xyz", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
