package com.nextportal.admin.authorization;

import com.nextportal.admin.member.MemberModels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.nextportal.admin.authorization.RoleModels.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Validated
@Tag(name = "03. 역할·권한 관리", description = "역할에 세부 권한을 묶고 사용자에게 여러 역할을 부여합니다. 기본 접근에는 admin:access가 필요합니다.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "입력값 또는 역할·권한 코드 오류", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "401", description = "JWT 또는 세션 만료. 권한 변경 후에는 다시 로그인해야 합니다.", content = @Content),
    @ApiResponse(responseCode = "403", description = "필요한 세부 권한 없음", content = @Content),
    @ApiResponse(responseCode = "404", description = "역할 또는 사용자 없음", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "409", description = "중복 코드, 시스템 역할 수정, 부여 중인 역할 삭제, 본인 역할 변경 또는 마지막 ADMIN 해제", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class RoleController {
    private final RoleService service;

    @GetMapping("/permissions")
    @Operation(operationId = "listPermissions", summary = "권한 카탈로그 조회", description = "role:read 필요. 권한 코드는 실제 API 정책과 함께 마이그레이션으로 관리합니다.")
    @ApiResponse(responseCode = "200", description = "권한 목록", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = PermissionView.class))))
    List<PermissionView> permissions() { return service.permissions(); }

    @GetMapping("/roles")
    @Operation(operationId = "listRoles", summary = "역할 목록 조회", description = "role:read 필요.")
    @ApiResponse(responseCode = "200", description = "역할 목록", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoleView.class))))
    List<RoleView> roles() { return service.roles(); }

    @GetMapping("/roles/{code}")
    @Operation(operationId = "getRole", summary = "역할 상세 조회", description = "role:read 필요.")
    @ApiResponse(responseCode = "200", description = "역할 정보", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleView.class)))
    RoleView get(@PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String code) { return service.get(code); }

    @PostMapping("/roles")
    @Operation(operationId = "createRole", summary = "역할 생성", description = "role:manage 필요. 알려진 권한 코드만 부여할 수 있습니다.")
    @ApiResponse(responseCode = "201", description = "역할 생성 완료", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleView.class)))
    ResponseEntity<RoleView> create(@Valid @RequestBody CreateRole request) {
        var role = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/roles/" + role.code())).body(role);
    }

    @PutMapping("/roles/{code}")
    @Operation(operationId = "updateRole", summary = "역할 이름·권한 교체", description = "role:manage 필요. 권한 변경 시 해당 역할의 모든 기존 Access·Refresh가 무효화됩니다. 본인에게 부여된 역할의 권한 변경은 거부합니다.")
    @ApiResponse(responseCode = "200", description = "수정된 역할", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleView.class)))
    RoleView update(@PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String code, @Valid @RequestBody UpdateRole request) { return service.update(code, request); }

    @DeleteMapping("/roles/{code}")
    @Operation(operationId = "deleteRole", summary = "역할 삭제", description = "role:manage 필요. 시스템 역할과 사용자에게 부여된 역할은 삭제할 수 없습니다.")
    @ApiResponse(responseCode = "204", description = "삭제 완료", content = @Content)
    ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/roles")
    @Operation(operationId = "assignUserRoles", summary = "사용자 역할 전체 교체", description = "role:manage 필요. 역할 변경 시 해당 사용자의 기존 Access·Refresh가 무효화됩니다. 본인 역할 변경, 빈 역할 목록, 마지막 활성 ADMIN 해제는 거부합니다.")
    @ApiResponse(responseCode = "200", description = "역할이 변경된 사용자", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberModels.View.class)))
    MemberModels.View assign(@PathVariable @Positive long id, @Valid @RequestBody AssignRoles request) { return service.assign(id, request); }
}
