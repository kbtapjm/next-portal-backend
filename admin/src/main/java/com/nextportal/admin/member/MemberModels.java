package com.nextportal.admin.member;

import com.nextportal.common.member.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class MemberModels {
    private MemberModels() { }
    public record Create(@NotBlank @Email @Size(max = 254) String email,
                         @NotBlank @Size(max = 100) String name,
                         @NotBlank @Size(min = 12, max = 72) String password,
                         @NotNull Role role) {
        @Override public String toString() { return "Create[password=REDACTED]"; }
    }
    public record Update(@NotBlank @Size(max = 100) String name, @NotNull Role role, @NotNull MemberStatus status) { }
    public record View(Long id, String email, String name, Role role, MemberStatus status, Instant createdAt) {
        public static View from(Member member) {
            return new View(member.getId(), member.getEmail(), member.getName(), member.getRole(), member.getStatus(), member.getCreatedAt());
        }
    }
    public record PageView(List<View> content, int page, int size, long totalElements, int totalPages) {
        public static PageView from(Page<Member> page) {
            return new PageView(page.getContent().stream().map(View::from).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
