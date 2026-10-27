package com.nextportal.api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentActor {
    private CurrentActor() { }

    public static long id() {
        return switch (SecurityContextHolder.getContext().getAuthentication().getPrincipal()) {
            case Jwt jwt -> Long.parseLong(jwt.getSubject());
            default -> throw new AccessDeniedException("JWT authentication required");
        };
    }

    public static boolean has(String permission) {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .anyMatch(value -> value.getAuthority().equals(permission));
    }
}
