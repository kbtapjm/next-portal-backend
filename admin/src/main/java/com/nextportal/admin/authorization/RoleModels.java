package com.nextportal.admin.authorization;

import com.nextportal.common.authorization.Permission;
import com.nextportal.common.authorization.PortalRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;

public final class RoleModels {
    private RoleModels() { }

    @Schema(name = "PermissionView", description = "API에서 검사하는 고정 권한 카탈로그. 권한 추가는 코드와 DB 마이그레이션으로 수행합니다.")
    public record PermissionView(String code, String description) {
        static PermissionView from(Permission permission) { return new PermissionView(permission.getCode(), permission.getDescription()); }
    }

    @Schema(name = "RoleView", description = "역할과 세부 권한. ADMIN·USER 시스템 역할은 변경·삭제할 수 없습니다.")
    public record RoleView(String code, String name, boolean system, long permissionRevision, List<String> permissions) {
        static RoleView from(PortalRole role) {
            return new RoleView(role.getCode(), role.getName(), role.isSystem(), role.getPermissionRevision(),
                role.getPermissions().stream().map(Permission::getCode).sorted().toList());
        }
    }

    @Schema(name = "CreateRole")
    public record CreateRole(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") @Schema(example = "AUDITOR") String code,
                             @NotBlank @Size(max = 100) @Schema(example = "감사 담당자") String name,
                             @NotNull @Size(max = 100) @Schema(example = "[\"admin:access\",\"user:read\"]")
                             Set<@NotBlank @Size(max = 80) String> permissions) { }

    @Schema(name = "UpdateRole", description = "이름과 권한 전체 교체. 권한이 달라지면 부여된 모든 계정의 기존 Access·Refresh가 무효화됩니다.")
    public record UpdateRole(@NotBlank @Size(max = 100) @Schema(example = "감사 담당자") String name,
                             @NotNull @Size(max = 100) @Schema(example = "[\"admin:access\",\"user:read\"]")
                             Set<@NotBlank @Size(max = 80) String> permissions) { }

    @Schema(name = "AssignRoles", description = "사용자의 역할 전체 교체. 빈 역할 목록과 본인 역할 변경은 허용하지 않습니다.")
    public record AssignRoles(@NotNull @Size(min = 1, max = 20) @Schema(example = "[\"USER_VIEWER\"]")
                              Set<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String> roles) { }
}
