package com.urlshortener.security;

import com.urlshortener.entity.UserRole;
import java.util.UUID;

/** The Authentication principal set by both the JWT and API-key filters. */
public record AuthenticatedUser(UUID id, String username, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
