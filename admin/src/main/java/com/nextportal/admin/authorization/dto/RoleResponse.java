package com.nextportal.admin.authorization.dto;

import com.nextportal.common.authorization.entity.Permission;
import com.nextportal.common.authorization.entity.PortalRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "RoleView", description = "역할과 세부 권한. ADMIN·USER 시스템 역할은 변경·삭제할 수 없습니다.")
public record RoleResponse(
    String code,
    String name,
    boolean system,
    long permissionRevision,
    List<String> permissions
) {

    public static RoleResponse from(PortalRole role) {
        return new RoleResponse(
            role.getCode(),
            role.getName(),
            role.isSystem(),
            role.getPermissionRevision(),
            role.getPermissions().stream().map(Permission::getCode).sorted().toList()
        );
    }
}
