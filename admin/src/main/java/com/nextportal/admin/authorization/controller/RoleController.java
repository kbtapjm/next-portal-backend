package com.nextportal.admin.authorization.controller;

import com.nextportal.admin.authorization.dto.PermissionResponse;
import com.nextportal.admin.authorization.dto.RoleAssignRequest;
import com.nextportal.admin.authorization.dto.RoleCreateRequest;
import com.nextportal.admin.authorization.dto.RoleResponse;
import com.nextportal.admin.authorization.dto.RoleUpdateRequest;
import com.nextportal.admin.authorization.service.RoleService;
import com.nextportal.admin.member.dto.MemberResponse;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Validated
@Tag(
    name = "03. 역할·권한 관리",
    description = "역할에 세부 권한을 묶고 사용자에게 여러 역할을 부여합니다. 기본 접근에는 admin:access가 필요합니다."
)
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(
        responseCode = "400",
        description = "입력값 또는 역할·권한 코드 오류",
        content = @Content(
            mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class)
        )
    ),
    @ApiResponse(
        responseCode = "401",
        description = "JWT 또는 세션 만료. 권한 변경 후에는 다시 로그인해야 합니다.",
        content = @Content
    ),
    @ApiResponse(responseCode = "403", description = "필요한 세부 권한 없음", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "역할 또는 사용자 없음",
        content = @Content(
            mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class)
        )
    ),
    @ApiResponse(
        responseCode = "409",
        description = "중복 코드, 시스템 역할 수정, 부여 중인 역할 삭제, 본인 역할 변경 또는 마지막 ADMIN 해제",
        content = @Content(
            mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
})
public class RoleController {

    private final RoleService service;

    @Operation(
        operationId = "listPermissions",
        summary = "권한 카탈로그 조회",
        description = "role:read 필요. 권한 코드는 실제 API 정책과 함께 마이그레이션으로 관리합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "권한 목록",
        content = @Content(
            mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = PermissionResponse.class))
        )
    )
    @GetMapping("/permissions")
    public List<PermissionResponse> permissions() {
        return service.permissions();
    }

    @Operation(operationId = "listRoles", summary = "역할 목록 조회", description = "role:read 필요.")
    @ApiResponse(
        responseCode = "200",
        description = "역할 목록",
        content = @Content(
            mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = RoleResponse.class))
        )
    )
    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return service.roles();
    }

    @Operation(operationId = "getRole", summary = "역할 상세 조회", description = "role:read 필요.")
    @ApiResponse(
        responseCode = "200",
        description = "역할 정보",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = RoleResponse.class)
        )
    )
    @GetMapping("/roles/{code}")
    public RoleResponse get(
        @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String code
    ) {
        return service.get(code);
    }

    @Operation(
        operationId = "createRole",
        summary = "역할 생성",
        description = "role:manage 필요. 알려진 권한 코드만 부여할 수 있습니다."
    )
    @ApiResponse(
        responseCode = "201",
        description = "역할 생성 완료",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = RoleResponse.class)
        )
    )
    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request) {
        var role = service.create(request);

        return ResponseEntity.created(URI.create("/api/admin/roles/" + role.code()))
            .body(role);
    }

    @Operation(
        operationId = "updateRole",
        summary = "역할 이름·권한 교체",
        description = "role:manage 필요. 권한 변경 시 해당 역할의 모든 기존 Access·Refresh가 무효화됩니다. "
            + "본인에게 부여된 역할의 권한 변경은 거부합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "수정된 역할",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = RoleResponse.class)
        )
    )
    @PutMapping("/roles/{code}")
    public RoleResponse update(
        @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String code,
        @Valid @RequestBody RoleUpdateRequest request
    ) {
        return service.update(code, request);
    }

    @Operation(
        operationId = "deleteRole",
        summary = "역할 삭제",
        description = "role:manage 필요. 시스템 역할과 사용자에게 부여된 역할은 삭제할 수 없습니다."
    )
    @ApiResponse(responseCode = "204", description = "삭제 완료", content = @Content)
    @DeleteMapping("/roles/{code}")
    public ResponseEntity<Void> delete(
        @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String code
    ) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        operationId = "assignUserRoles",
        summary = "사용자 역할 전체 교체",
        description = "role:manage 필요. 역할 변경 시 해당 사용자의 기존 Access·Refresh가 무효화됩니다. "
            + "본인 역할 변경, 빈 역할 목록, 마지막 활성 ADMIN 해제는 거부합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "역할이 변경된 사용자",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = MemberResponse.class)
        )
    )
    @PutMapping("/users/{id}/roles")
    public MemberResponse assign(
        @PathVariable @Positive long id,
        @Valid @RequestBody RoleAssignRequest request
    ) {
        return service.assign(id, request);
    }
}
