package com.nextportal.admin.member;

import com.nextportal.common.member.*;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class MemberModels {
    private MemberModels() { }
    @Schema(name = "CreateUser", description = "사용자 등록 요청")
    public record Create(@Schema(description = "로그인 이메일", example = "user@example.com") @NotBlank @Email @Size(max = 254) String email,
                         @Schema(description = "사용자 이름", example = "홍길동") @NotBlank @Size(max = 100) String name,
                         @Schema(description = "12자 이상, UTF-8 기준 72바이트 이하", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
                         @NotBlank @Size(min = 12, max = 72) String password,
                         @Schema(example = "USER") @NotNull Role role) {
        @Override public String toString() { return "Create[password=REDACTED]"; }
    }
    @Schema(name = "UpdateUser", description = "사용자 이름·역할·상태 수정")
    public record Update(@Schema(example = "홍길동") @NotBlank @Size(max = 100) String name,
                         @Schema(example = "USER") @NotNull Role role,
                         @Schema(example = "ACTIVE") @NotNull MemberStatus status) { }
    @Schema(name = "MemberView", description = "비밀번호를 포함하지 않는 사용자 정보")
    public record View(@Schema(example = "1") Long id,
                       @Schema(example = "user@example.com") String email,
                       @Schema(example = "홍길동") String name, Role role, MemberStatus status, Instant createdAt) {
        public static View from(Member member) {
            return new View(member.getId(), member.getEmail(), member.getName(), member.getRole(), member.getStatus(), member.getCreatedAt());
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
