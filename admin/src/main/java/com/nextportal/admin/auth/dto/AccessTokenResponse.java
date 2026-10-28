package com.nextportal.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Token", description = "Access JWT와 유효기간. Refresh 토큰은 HttpOnly 쿠키로 전달됩니다.")
public record AccessTokenResponse(
    @Schema(description = "Authorize에 입력할 Access JWT")
    String accessToken,

    @Schema(example = "Bearer")
    String tokenType,

    @Schema(description = "유효기간(초)", example = "900")
    long expiresIn
) {
}
