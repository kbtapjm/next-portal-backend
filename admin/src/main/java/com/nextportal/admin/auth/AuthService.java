package com.nextportal.admin.auth;

import com.nextportal.api.error.PortalException;
import com.nextportal.api.security.*;
import com.nextportal.common.member.*;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final MemberRepository members;
    private final PasswordEncoder passwords;
    private final SessionStore sessions;
    private final JwtTokens tokens;
    private final SecurityProperties properties;
    private String dummyHash;

    @PostConstruct
    void initializeDummyHash() {
        dummyHash = passwords.encode(JwtTokens.randomToken());
    }

    public Issued login(String email, String password) {
        var candidate = members.findByEmail(Member.normalizeEmail(email));
        var matches = password.getBytes(StandardCharsets.UTF_8).length <= 72
            && passwords.matches(password, candidate.map(Member::getPasswordHash).orElse(dummyHash));
        var member = candidate.filter(value -> matches).filter(AuthService::isAdmin).orElseThrow(AuthService::unauthorized);
        var sid = UUID.randomUUID().toString();
        var refresh = JwtTokens.randomToken();
        var jti = UUID.randomUUID().toString();
        var access = tokens.access(member, sid, jti);
        sessions.create(sid, member.getId(), member.getAuthVersion(), JwtTokens.digest(refresh), jti, properties.refreshTtl());
        return new Issued(access, properties.accessTtl().toSeconds(), sid + "." + refresh);
    }
    public Issued refresh(String cookie) {
        var parts = Optional.ofNullable(cookie).map(value -> value.split("\\.", 2))
            .filter(value -> value.length == 2).orElseThrow(AuthService::unauthorized);
        var sid = parts[0];
        var previous = JwtTokens.digest(parts[1]);
        var session = sessions.find(sid).filter(value -> value.refreshHash().equals(previous)).orElseThrow(AuthService::unauthorized);
        var member = members.findById(session.memberId()).filter(AuthService::isAdmin)
            .filter(value -> value.getAuthVersion() == session.version()).orElseThrow(AuthService::unauthorized);
        var refresh = JwtTokens.randomToken();
        var jti = UUID.randomUUID().toString();
        var access = tokens.access(member, sid, jti);
        Optional.of(sessions.rotate(sid, previous, JwtTokens.digest(refresh), jti, properties.refreshTtl()))
            .filter(Boolean::booleanValue).orElseThrow(AuthService::unauthorized);
        return new Issued(access, properties.accessTtl().toSeconds(), sid + "." + refresh);
    }
    public void logout(String sid) { sessions.delete(sid); }
    private static boolean isAdmin(Member member) { return member.getRole() == Role.ADMIN && member.getStatus() == MemberStatus.ACTIVE; }
    private static PortalException unauthorized() { return new PortalException(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."); }
    public record Issued(String accessToken, long expiresIn, String refreshCookie) {
        @Override public String toString() { return "Issued[REDACTED]"; }
    }
}
