package com.nextportal.api.security;

import com.nextportal.common.member.Member;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokens {
    private final JwtEncoder encoder;
    private final SecurityProperties properties;

    public String access(Member member, PortalAuthorization.Snapshot authorization, String sid, String jti) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(member.getId().toString())
            .issuedAt(now).expiresAt(now.plus(properties.accessTtl())).id(jti)
            .audience(java.util.List.of("next-portal"))
            .claim("sid", sid).claim("ver", member.getAuthVersion()).claim("authz", authorization.version()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    public static String randomToken() { return UUID.randomUUID().toString() + UUID.randomUUID(); }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
