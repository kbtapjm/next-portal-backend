package com.nextportal.admin.member.dto;

import com.nextportal.common.member.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(
    name = "CreateUser",
    description = "사용자 등록 요청",
    example = "{\"email\":\"user@example.com\",\"name\":\"홍길동\",\"password\":\"example-password-123!\",\"roles\":[\"USER\"]}"
)
public record MemberCreateRequest(
    @Schema(description = "로그인 이메일", example = "user@example.com")
    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @Schema(description = "사용자 이름", example = "홍길동")
    @NotBlank
    @Size(max = 100)
    String name,

    @Schema(
        description = "12자 이상, UTF-8 기준 72바이트 이하",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @NotBlank
    @Size(min = 12, max = 72)
    String password,

    @Schema(
        description = "기존 클라이언트 호환용. roles와 동시 지정할 수 없습니다.",
        example = "USER",
        deprecated = true
    )
    Role role,

    @Schema(
        description = "부여할 역할 코드. 미지정 시 USER. role과 동시 지정할 수 없습니다.",
        example = "[\"USER\"]"
    )
    @Size(min = 1, max = 20)
    Set<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String> roles
) {

    @Override
    public String toString() {
        return "Create[password=REDACTED]";
    }
}
