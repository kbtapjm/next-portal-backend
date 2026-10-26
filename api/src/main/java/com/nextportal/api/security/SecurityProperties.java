package com.nextportal.api.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portal.security")
public record SecurityProperties(String issuer, String secret, Duration accessTtl, Duration refreshTtl,
                                 List<String> allowedOrigins, boolean secureCookie) {
    public SecurityProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("portal.security.secret must contain at least 32 bytes");
        }
    }
    @Override public String toString() { return "SecurityProperties[secret=REDACTED]"; }
}
