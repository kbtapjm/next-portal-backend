package com.nextportal.admin.member;

import com.nextportal.common.member.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.nextportal.admin.member.MemberModels.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@Validated
@Tag(name = "02. 사용자 관리", description = "조회 user:read, 생성·수정 user:write. 역할 부여와 권한 관리자 수정에는 role:manage도 필요합니다.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "입력값 검증 실패", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
    @ApiResponse(responseCode = "401", description = "유효한 Access JWT 필요", content = @Content),
    @ApiResponse(responseCode = "403", description = "admin:access 또는 해당 작업의 세부 권한 없음", content = @Content)
})
public class MemberController {
    private final MemberService service;
    @GetMapping
    @Operation(operationId = "searchUsers", summary = "사용자 목록 검색", description = "user:read 필요. 이름·이메일 부분 검색, 역할·상태 필터를 지원합니다. roleCode는 실제 역할 코드, role은 호환용 관리자 접근 여부 분류입니다. 최신 등록순, 페이지 번호는 0부터 시작합니다.")
    @ApiResponse(responseCode = "200", description = "사용자 검색 결과", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageView.class)))
    PageView search(@Parameter(description = "이름 또는 이메일 검색어", example = "홍길동") @RequestParam(required = false) @Size(max = 100) String keyword,
                    @Parameter(deprecated = true) @RequestParam(required = false) Role role,
                    @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String roleCode,
                    @RequestParam(required = false) MemberStatus status,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(keyword, role, roleCode, status, page, size);
    }
    @Operation(operationId = "getUser", summary = "사용자 조회", description = "user:read 필요.")
    @ApiResponse(responseCode = "200", description = "사용자 정보", content = @Content(mediaType = "application/json", schema = @Schema(implementation = View.class)))
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    @GetMapping("/{id}") View get(@PathVariable @Positive long id) { return service.get(id); }
    @Operation(operationId = "createUser", summary = "사용자 등록", description = "user:write 필요. roles 생략 시 USER. USER 이외 역할 부여에는 role:manage도 필요합니다. 이메일은 소문자 정규화, 비밀번호는 12자 이상 UTF-8 72바이트 이하입니다.")
    @ApiResponse(responseCode = "201", description = "사용자 생성 완료", content = @Content(mediaType = "application/json", schema = @Schema(implementation = View.class)))
    @ApiResponse(responseCode = "409", description = "이미 등록된 이메일", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    @PostMapping ResponseEntity<View> create(@Valid @RequestBody Create request) {
        var member = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/users/" + member.id())).body(member);
    }
    @Operation(operationId = "updateUser", summary = "사용자 정보 수정", description = "user:write 필요. 역할 변경 또는 role:manage 보유 계정 수정에는 role:manage도 필요합니다. 본인 역할 변경·비활성화, 마지막 활성 ADMIN 해제는 거부됩니다. 역할·상태 변경 시 기존 Access·Refresh가 무효화됩니다.")
    @ApiResponse(responseCode = "200", description = "수정된 사용자 정보", content = @Content(mediaType = "application/json", schema = @Schema(implementation = View.class)))
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "본인 권한 해제·비활성화 또는 동시 수정 충돌", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    @PutMapping("/{id}") View update(@PathVariable @Positive long id,
                                    @Valid @RequestBody Update request) {
        return service.update(id, request);
    }
}
