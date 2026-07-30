package com.urlshortener.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.cache.RedisRateLimiter;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.security.RateLimitFilter;
import com.urlshortener.security.RestAccessDeniedHandler;
import com.urlshortener.security.RestAuthenticationEntryPoint;
import com.urlshortener.security.apikey.ApiKeyAuthenticationFilter;
import com.urlshortener.security.apikey.ApiKeyHasher;
import com.urlshortener.security.jwt.JwtAuthenticationFilter;
import com.urlshortener.security.jwt.JwtTokenProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless, token-based security. CSRF is intentionally disabled: CSRF protection
 * exists to stop a browser from auto-attaching a victim's session cookie to a forged
 * cross-site request. This API carries no session cookie - auth is a bearer token or
 * API key that a forged request from another origin cannot obtain - so CSRF simply
 * does not apply to this threat model (see docs/ARCHITECTURE.md for the full writeup).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final RedisRateLimiter redisRateLimiter;
    private final ObjectMapper objectMapper;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${app.rate-limit.default-capacity}")
    private int rateLimitDefaultCapacity;

    @Value("${app.rate-limit.default-refill-seconds}")
    private int rateLimitDefaultWindowSeconds;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenProvider);
        ApiKeyAuthenticationFilter apiKeyFilter = new ApiKeyAuthenticationFilter(apiKeyRepository, apiKeyHasher);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(
            redisRateLimiter, objectMapper, rateLimitDefaultCapacity, rateLimitDefaultWindowSeconds);
        CorrelationIdFilter correlationIdFilter = new CorrelationIdFilter();

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/urls/*/verify-password").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/*").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiKeyFilter, JwtAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, ApiKeyAuthenticationFilter.class)
            .addFilterBefore(correlationIdFilter, RateLimitFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
