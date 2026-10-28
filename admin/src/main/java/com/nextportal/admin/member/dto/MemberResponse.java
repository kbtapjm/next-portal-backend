package com.nextportal.admin.member.dto;

import com.nextportal.common.member.entity.Member;
import com.nextportal.common.member.entity.MemberStatus;
import com.nextportal.common.member.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "MemberView", description = "비밀번호를 포함하지 않는 사용자 정보")
public record MemberResponse(
    @Schema(example = "1")
    Long id,

    @Schema(example = "user@example.com")
    String email,

    @Schema(example = "홍길동")
    String name,

    @Schema(
        description = "호환용 분류. admin:access 보유 시 ADMIN, 그 외 USER. 실제 역할은 roles를 사용하세요.",
        deprecated = true
    )
    Role role,

    MemberStatus status,
    Instant createdAt,
    List<String> roles,
    List<String> permissions
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
            member.getId(),
            member.getEmail(),
            member.getName(),
            member.getRole(),
            member.getStatus(),
            member.getCreatedAt(),
            member.roleCodes(),
            member.permissionCodes()
        );
    }
}
