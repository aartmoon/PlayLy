package com.playly.core.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID userId(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
