package com.urlshortener.config;

import com.urlshortener.entity.User;
import com.urlshortener.entity.UserRole;
import com.urlshortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds two demo accounts (idempotent - skipped if they already exist) so the platform
 * is immediately usable after `docker compose up --build` without a manual signup step.
 * Disable for anything beyond local demo purposes via app.seed-demo-data=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-demo-data:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled) {
            return;
        }
        seedIfAbsent("demo", "demo@example.com", "Demo@12345", UserRole.USER);
        seedIfAbsent("admin", "admin@example.com", "Admin@12345", UserRole.ADMIN);
        log.info("Demo accounts ready: demo/Demo@12345 (USER), admin/Admin@12345 (ADMIN). "
            + "Set SEED_DEMO_DATA=false to disable this outside local/demo use.");
    }

    private void seedIfAbsent(String username, String email, String rawPassword, UserRole role) {
        if (userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(username)) {
            return;
        }
        User user = User.builder()
            .username(username)
            .email(email)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(role)
            .enabled(true)
            .build();
        userRepository.save(user);
    }
}
