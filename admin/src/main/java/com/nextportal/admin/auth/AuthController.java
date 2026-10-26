package com.nextportal.admin.auth;

import com.nextportal.api.error.PortalException;
import com.nextportal.api.security.SecurityProperties;
import com.nextportal.admin.member.MemberService;
import com.nextportal.admin.member.MemberModels.View;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
public class AuthController {
    private static final String COOKIE = "portal_admin_refresh";
    private final AuthService auth;
    private final MemberService members;
    private final SecurityProperties properties;
    @PostMapping("/login") ResponseEntity<TokenView> login(@RequestHeader(value = "Origin", required = false) String origin,
                                                           @Valid @RequestBody Login request) {
        requireOrigin(origin);
        return issued(auth.login(request.email(), request.password()));
    }
    @PostMapping("/refresh") ResponseEntity<TokenView> refresh(@RequestHeader(value = "Origin", required = false) String origin,
                         @CookieValue(value = COOKIE, required = false) String cookie) {
        requireOrigin(origin);
        return issued(auth.refresh(cookie));
    }
    @PostMapping("/logout") ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        auth.logout(jwt.getClaimAsString("sid"));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }
    @GetMapping("/me") View me(@AuthenticationPrincipal Jwt jwt) { return members.get(Long.parseLong(jwt.getSubject())); }
    private ResponseEntity<TokenView> issued(AuthService.Issued issued) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, cookie(issued.refreshCookie(), properties.refreshTtl()).toString())
            .body(new TokenView(issued.accessToken(), "Bearer", issued.expiresIn()));
    }
    private ResponseCookie cookie(String value, Duration ttl) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(properties.secureCookie())
            .sameSite("Strict").path("/api/admin/auth").maxAge(ttl).build();
    }
    private void requireOrigin(String origin) {
        Optional.ofNullable(origin).filter(properties.allowedOrigins()::contains)
            .orElseThrow(() -> new PortalException(HttpStatus.FORBIDDEN, "허용되지 않은 요청 출처입니다."));
    }
    public record Login(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "Login[REDACTED]"; }
    }
    public record TokenView(String accessToken, String tokenType, long expiresIn) { }
}
