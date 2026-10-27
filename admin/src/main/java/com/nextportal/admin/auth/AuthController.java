package com.nextportal.admin.auth;

import com.nextportal.api.error.PortalException;
import com.nextportal.api.security.SecurityProperties;
import com.nextportal.admin.member.MemberService;
import com.nextportal.admin.member.MemberModels.View;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "01. 관리자 인증", description = "관리자 로그인, 내 정보, 토큰 갱신과 로그아웃")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "401", description = "인증 정보가 올바르지 않거나 세션이 만료·폐기됨. 보안 필터 응답은 본문이 없을 수 있습니다.", content = @Content),
    @ApiResponse(responseCode = "403", description = "허용되지 않은 Origin 또는 관리자 권한 없음", content = @Content)
})
public class AuthController {
    private static final String COOKIE = "portal_admin_refresh";
    private final AuthService auth;
    private final MemberService members;
    private final SecurityProperties properties;
    @Operation(operationId = "adminLogin", summary = "관리자 로그인", description = "활성 계정 중 admin:access 권한 보유 계정만 로그인할 수 있습니다. "
        + "Origin은 허용 출처와 일치해야 하며 브라우저가 실제 출처로 설정합니다. Swagger에서는 현재 서버 출처를 사용하세요. "
        + "Refresh 쿠키가 자동 저장되며 응답의 accessToken으로 Authorize를 설정합니다.")
    @ApiResponse(responseCode = "200", description = "Access JWT 발급 및 Refresh 쿠키 저장",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenView.class)), headers = {
        @Header(name = "Set-Cookie", description = "portal_admin_refresh 쿠키. HttpOnly, SameSite=Strict, Path=/api/admin/auth", schema = @Schema(type = "string")),
        @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store"))
    })
    @PostMapping("/login") ResponseEntity<TokenView> login(
            @Parameter(description = "허용된 출처. 브라우저에서는 실제 Origin이 자동 적용됩니다.", required = true, example = "http://localhost:8080")
            @RequestHeader(value = "Origin", required = false) String origin,
                                                           @Valid @RequestBody Login request) {
        requireOrigin(origin);
        return issued(auth.login(request.email(), request.password()));
    }
    @Operation(operationId = "adminRefresh", summary = "토큰 갱신", description = "로그인으로 저장된 HttpOnly Refresh 쿠키를 자동 전송합니다. "
        + "직접 쿠키 값을 입력할 필요는 없습니다. 쿠키는 한 번만 사용 가능하며 이전 Access JWT도 거부됩니다. "
        + "응답의 새 accessToken으로 Authorize를 다시 설정하세요.", security = @SecurityRequirement(name = "refreshCookie"))
    @ApiResponse(responseCode = "200", description = "Access JWT 재발급 및 Refresh 쿠키 회전",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenView.class)))
    @PostMapping("/refresh") ResponseEntity<TokenView> refresh(
            @Parameter(description = "브라우저가 자동 설정하는 허용 출처", required = true, example = "http://localhost:8080")
            @RequestHeader(value = "Origin", required = false) String origin,
            @Parameter(hidden = true) @CookieValue(value = COOKIE, required = false) String cookie) {
        requireOrigin(origin);
        return issued(auth.refresh(cookie));
    }
    @Operation(operationId = "adminLogout", summary = "로그아웃", description = "현재 Redis 세션을 삭제하고 Refresh 쿠키를 만료시킵니다.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "세션 폐기 완료", content = @Content)
    @PostMapping("/logout") ResponseEntity<Void> logout(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        auth.logout(jwt.getClaimAsString("sid"));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }
    @Operation(operationId = "adminMe", summary = "현재 관리자 및 권한 조회", description = "roles는 부여된 역할, permissions는 합산된 세부 권한입니다. user:read 없이 자신의 정보만 조회할 수 있습니다.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "현재 관리자 정보", content = @Content(mediaType = "application/json", schema = @Schema(implementation = View.class)))
    @GetMapping("/me") View me() { return members.current(); }
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
    @Schema(name = "AdminLogin", description = "관리자 로그인 요청")
    public record Login(@Schema(description = "관리자 이메일", example = "admin@portal.local")
                        @NotBlank @Email @Size(max = 254) String email,
                        @Schema(description = "등록된 관리자 비밀번호", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
                        @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "Login[REDACTED]"; }
    }
    @Schema(name = "Token", description = "Access JWT와 유효기간. Refresh 토큰은 HttpOnly 쿠키로 전달됩니다.")
    public record TokenView(@Schema(description = "Authorize에 입력할 Access JWT") String accessToken,
                            @Schema(example = "Bearer") String tokenType,
                            @Schema(description = "유효기간(초)", example = "900") long expiresIn) { }
}
