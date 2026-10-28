package com.nextportal.admin.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(name = "AssignRoles", description = "사용자의 역할 전체 교체. 빈 역할 목록과 본인 역할 변경은 허용하지 않습니다.")
public record RoleAssignRequest(
    @Schema(example = "[\"USER_VIEWER\"]")
    @NotNull
    @Size(min = 1, max = 20)
    Set<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String> roles
) {
}
