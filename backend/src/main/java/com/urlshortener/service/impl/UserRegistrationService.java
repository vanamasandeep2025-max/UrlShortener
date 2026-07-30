package com.urlshortener.service.impl;

import com.urlshortener.entity.User;
import com.urlshortener.entity.UserRole;
import com.urlshortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the transaction boundary for creating a user. Deliberately a separate bean from
 * AuthServiceImpl (rather than an inline @Transactional block there): AuditService.log()
 * runs in Propagation.REQUIRES_NEW, which suspends the caller's transaction and opens a
 * genuinely separate one on another connection - it can never see a row that transaction
 * hasn't committed yet, regardless of flushing. Calling out to this bean's own commit
 * boundary from AuthServiceImpl.register() (itself no longer @Transactional) lets the new
 * user's row actually commit before the audit log entry - which references that same user
 * as its actor - is written.
 */
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(String username, String email, String rawPassword) {
        User user = User.builder()
            .username(username)
            .email(email)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(UserRole.USER)
            .enabled(true)
            .build();
        return userRepository.save(user);
    }
}
