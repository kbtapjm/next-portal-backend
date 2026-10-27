package com.nextportal.admin.member;

import com.nextportal.common.member.*;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;

public final class MemberModels {
    private MemberModels() { }
    @Schema(name = "CreateUser", description = "사용자 등록 요청",
        example = "{\"email\":\"user@example.com\",\"name\":\"홍길동\",\"password\":\"example-password-123!\",\"roles\":[\"USER\"]}")
    public record Create(@Schema(description = "로그인 이메일", example = "user@example.com") @NotBlank @Email @Size(max = 254) String email,
                         @Schema(description = "사용자 이름", example = "홍길동") @NotBlank @Size(max = 100) String name,
                         @Schema(description = "12자 이상, UTF-8 기준 72바이트 이하", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
                         @NotBlank @Size(min = 12, max = 72) String password,
                         @Schema(description = "기존 클라이언트 호환용. roles와 동시 지정할 수 없습니다.", example = "USER", deprecated = true) Role role,
                         @Schema(description = "부여할 역할 코드. 미지정 시 USER. role과 동시 지정할 수 없습니다.", example = "[\"USER\"]")
                         @Size(min = 1, max = 20) Set<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String> roles) {
        @Override public String toString() { return "Create[password=REDACTED]"; }
    }
    @Schema(name = "UpdateUser", description = "사용자 이름·역할·상태 수정",
        example = "{\"name\":\"홍길동\",\"status\":\"ACTIVE\",\"roles\":[\"USER_VIEWER\"]}")
    public record Update(@Schema(example = "홍길동") @NotBlank @Size(max = 100) String name,
                         @Schema(description = "호환용 ADMIN/USER 분류. 현재 분류와 같으면 기존 역할 유지. roles와 동시 지정 불가.", example = "USER", deprecated = true) Role role,
                         @Schema(example = "ACTIVE") @NotNull MemberStatus status,
                         @Schema(description = "역할 전체 교체. 생략 시 유지. 변경에는 role:manage가 필요합니다.", example = "[\"USER_VIEWER\"]")
                         @Size(min = 1, max = 20) Set<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String> roles) { }
    @Schema(name = "MemberView", description = "비밀번호를 포함하지 않는 사용자 정보")
    public record View(@Schema(example = "1") Long id,
                       @Schema(example = "user@example.com") String email,
                       @Schema(example = "홍길동") String name,
                       @Schema(description = "호환용 분류. admin:access 보유 시 ADMIN, 그 외 USER. 실제 역할은 roles를 사용하세요.", deprecated = true) Role role,
                       MemberStatus status, Instant createdAt, List<String> roles, List<String> permissions) {
        public static View from(Member member) {
            return new View(member.getId(), member.getEmail(), member.getName(), member.getRole(), member.getStatus(), member.getCreatedAt(),
                member.roleCodes(), member.permissionCodes());
        }
    }
    @Schema(name = "MemberPage", description = "사용자 검색 결과. page는 0부터 시작합니다.")
    public record PageView(List<View> content, int page, int size, long totalElements, int totalPages) {
        public static PageView from(Page<Member> page) {
            return new PageView(page.getContent().stream().map(View::from).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
