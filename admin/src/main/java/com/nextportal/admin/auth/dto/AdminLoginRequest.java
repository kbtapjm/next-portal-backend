package com.nextportal.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "AdminLogin", description = "관리자 로그인 요청")
public record AdminLoginRequest(
    @Schema(description = "관리자 이메일", example = "admin@portal.local")
    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @Schema(
        description = "등록된 관리자 비밀번호",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @NotBlank
    @Size(max = 72)
    String password
) {

    @Override
    public String toString() {
        return "Login[REDACTED]";
    }
}
