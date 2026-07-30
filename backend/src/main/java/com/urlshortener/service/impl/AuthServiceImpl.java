package com.urlshortener.service.impl;

import com.urlshortener.audit.AuditService;
import com.urlshortener.dto.request.LoginRequest;
import com.urlshortener.dto.request.RefreshTokenRequest;
import com.urlshortener.dto.request.RegisterRequest;
import com.urlshortener.dto.response.AuthResponse;
import com.urlshortener.entity.ActorType;
import com.urlshortener.entity.User;
import com.urlshortener.exception.DuplicateResourceException;
import com.urlshortener.exception.InvalidTokenException;
import com.urlshortener.repository.UserRepository;
import com.urlshortener.security.jwt.JwtTokenProvider;
import com.urlshortener.service.AuthService;
import io.jsonwebtoken.Claims;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;
    private final UserRegistrationService userRegistrationService;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(request.getUsername())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered");
        }

        // Not wrapped in this method's own transaction: UserRegistrationService.createUser
        // has its own @Transactional boundary that must actually COMMIT before control
        // returns here - see UserRegistrationService's Javadoc for why (AuditService.log's
        // REQUIRES_NEW propagation cannot see an uncommitted row from a still-open outer
        // transaction, no matter how it's flushed).
        User user = userRegistrationService.createUser(request.getUsername(), request.getEmail(), request.getPassword());

        auditService.log(ActorType.USER, user.getId(), "USER_REGISTERED", "USER", user.getId().toString(),
            Map.of("username", user.getUsername()), null);

        return issueTokenPair(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(request.getUsername())
            .orElseGet(() -> {
                auditService.log(ActorType.ANONYMOUS, null, "LOGIN_FAILURE", "USER", null,
                    Map.of("username", request.getUsername(), "reason", "unknown_username"), null);
                throw new BadCredentialsException("Invalid username or password");
            });

        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditService.log(ActorType.USER, user.getId(), "LOGIN_FAILURE", "USER", user.getId().toString(),
                Map.of("reason", user.isEnabled() ? "bad_password" : "account_disabled"), null);
            throw new BadCredentialsException("Invalid username or password");
        }

        auditService.log(ActorType.USER, user.getId(), "LOGIN_SUCCESS", "USER", user.getId().toString(), null, null);
        return issueTokenPair(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        Claims claims = jwtTokenProvider.parseClaims(request.getRefreshToken());
        if (!jwtTokenProvider.isRefreshToken(claims)) {
            throw new InvalidTokenException("Provided token is not a refresh token");
        }
        UUID userId = jwtTokenProvider.getUserId(claims);
        User user = userRepository.findById(userId)
            .filter(u -> u.getDeletedAt() == null && u.isEnabled())
            .orElseThrow(() -> new InvalidTokenException("User account is no longer active"));

        return issueTokenPair(user);
    }

    private AuthResponse issueTokenPair(User user) {
        String role = user.getRole().name();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername(), role);
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresInMs(jwtTokenProvider.getAccessExpirationMs())
            .build();
    }
}
