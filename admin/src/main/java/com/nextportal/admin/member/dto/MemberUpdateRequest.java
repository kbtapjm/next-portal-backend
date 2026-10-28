package com.nextportal.admin.member.dto;

import com.nextportal.common.member.entity.MemberStatus;
import com.nextportal.common.member.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(
    name = "UpdateUser",
    description = "사용자 이름·역할·상태 수정",
    example = "{\"name\":\"홍길동\",\"status\":\"ACTIVE\",\"roles\":[\"USER_VIEWER\"]}"
)
public record MemberUpdateRequest(
    @Schema(example = "홍길동")
    @NotBlank
    @Size(max = 100)
    String name,

    @Schema(
        description = "호환용 ADMIN/USER 분류. 현재 분류와 같으면 기존 역할 유지. roles와 동시 지정 불가.",
        example = "USER",
        deprecated = true
    )
    Role role,

    @Schema(example = "ACTIVE")
    @NotNull
    MemberStatus status,

    @Schema(
        description = "역할 전체 교체. 생략 시 유지. 변경에는 role:manage가 필요합니다.",
        example = "[\"USER_VIEWER\"]"
    )
    @Size(min = 1, max = 20)
    Set<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String> roles
) {
}
