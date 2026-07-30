package com.urlshortener.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.urlshortener.audit.AuditService;
import com.urlshortener.dto.request.LoginRequest;
import com.urlshortener.dto.request.RefreshTokenRequest;
import com.urlshortener.dto.request.RegisterRequest;
import com.urlshortener.dto.response.AuthResponse;
import com.urlshortener.entity.User;
import com.urlshortener.entity.UserRole;
import com.urlshortener.exception.DuplicateResourceException;
import com.urlshortener.exception.InvalidTokenException;
import com.urlshortener.repository.UserRepository;
import com.urlshortener.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuditService auditService;
    @Mock private UserRegistrationService userRegistrationService;
    @Mock private Claims claims;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtTokenProvider, auditService, userRegistrationService);
    }

    private User activeUser() {
        return User.builder().id(UUID.randomUUID()).username("alice").email("alice@example.com")
            .passwordHash("hashed").role(UserRole.USER).enabled(true).build();
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull("alice")).thenReturn(true);

        RegisterRequest request = RegisterRequest.builder().username("alice").email("a@b.com").password("Passw0rd").build();

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull("alice")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("alice@example.com")).thenReturn(true);

        RegisterRequest request = RegisterRequest.builder().username("alice").email("alice@example.com").password("Passw0rd").build();

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void registerIssuesTokenPairOnSuccess() {
        when(userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull("alice")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("alice@example.com")).thenReturn(false);
        when(userRegistrationService.createUser("alice", "alice@example.com", "Passw0rd!")).thenAnswer(inv ->
            User.builder().id(UUID.randomUUID()).username("alice").email("alice@example.com")
                .passwordHash("hashed").role(UserRole.USER).enabled(true).build());
        when(jwtTokenProvider.generateAccessToken(any(), anyString(), anyString())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(), anyString(), anyString())).thenReturn("refresh-token");

        RegisterRequest request = RegisterRequest.builder().username("alice").email("alice@example.com").password("Passw0rd!").build();

        AuthResponse response = authService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginRejectsUnknownUsername() {
        when(userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull("ghost")).thenReturn(Optional.empty());

        LoginRequest request = LoginRequest.builder().username("ghost").password("whatever").build();

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = activeUser();
        when(userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        LoginRequest request = LoginRequest.builder().username("alice").password("wrong").build();

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRejectsDisabledAccountEvenWithCorrectPassword() {
        User user = activeUser();
        user.setEnabled(false);
        when(userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));

        LoginRequest request = LoginRequest.builder().username("alice").password("Passw0rd!").build();

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginSucceedsAndIssuesTokens() {
        User user = activeUser();
        when(userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), anyString(), anyString())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(), anyString(), anyString())).thenReturn("refresh-token");

        LoginRequest request = LoginRequest.builder().username("alice").password("Passw0rd!").build();

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void refreshRejectsAccessTokenPassedAsRefreshToken() {
        when(jwtTokenProvider.parseClaims("some-token")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(false);

        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("some-token").build();

        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshRejectsWhenUserNoLongerActive() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseClaims("refresh-tok")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh-tok").build();

        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshIssuesNewTokenPairForActiveUser() {
        User user = activeUser();
        when(jwtTokenProvider.parseClaims("refresh-tok")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(), anyString(), anyString())).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(any(), anyString(), anyString())).thenReturn("new-refresh");

        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("refresh-tok").build();

        AuthResponse response = authService.refresh(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    }
}
