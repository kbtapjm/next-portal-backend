package com.nextportal.admin.authorization.dto;

import com.nextportal.common.authorization.entity.Permission;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PermissionView", description = "API에서 검사하는 고정 권한 카탈로그. 권한 추가는 코드와 DB 마이그레이션으로 수행합니다.")
public record PermissionResponse(
    String code,
    String description
) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getCode(), permission.getDescription());
    }
}
