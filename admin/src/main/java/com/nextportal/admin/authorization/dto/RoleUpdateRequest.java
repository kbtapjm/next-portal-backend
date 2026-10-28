package com.nextportal.admin.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(name = "UpdateRole", description = "이름과 권한 전체 교체. 권한이 달라지면 부여된 모든 계정의 기존 Access·Refresh가 무효화됩니다.")
public record RoleUpdateRequest(
    @Schema(example = "감사 담당자")
    @NotBlank
    @Size(max = 100)
    String name,

    @Schema(example = "[\"admin:access\",\"user:read\"]")
    @NotNull
    @Size(max = 100)
    Set<@NotBlank @Size(max = 80) String> permissions
) {
}
