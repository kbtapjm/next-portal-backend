package com.nextportal.admin.member.service;

import com.nextportal.admin.authorization.service.RoleAssignments;
import com.nextportal.admin.member.dto.MemberCreateRequest;
import com.nextportal.admin.member.dto.MemberPageResponse;
import com.nextportal.admin.member.dto.MemberResponse;
import com.nextportal.admin.member.dto.MemberUpdateRequest;
import com.nextportal.api.error.PortalException;
import com.nextportal.api.security.CurrentActor;
import com.nextportal.common.member.entity.Member;
import com.nextportal.common.member.entity.MemberStatus;
import com.nextportal.common.member.entity.Role;
import com.nextportal.common.member.repository.MemberQueries;
import com.nextportal.common.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("denyAll()")
public class MemberService {

    private final MemberRepository members;
    private final MemberQueries queries;
    private final PasswordEncoder passwords;
    private final RoleAssignments assignments;

    @PreAuthorize("hasAuthority('user:read')")
    public MemberPageResponse search(String keyword, Role role, String roleCode, MemberStatus status, int page, int size) {
        return MemberPageResponse.from(queries.search(keyword, role, roleCode, status, page, size));
    }

    @PreAuthorize("hasAuthority('user:read')")
    public MemberResponse get(long id) {
        return MemberResponse.from(require(id));
    }

    @PreAuthorize("hasAuthority('admin:access')")
    public MemberResponse current() {
        return MemberResponse.from(require(CurrentActor.id()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('user:write')")
    public MemberResponse create(MemberCreateRequest request) {
        assignments.lock();
        rejectAmbiguousRoles(request.role(), request.roles());

        var codes = Optional.ofNullable(request.roles())
            .orElseGet(() -> Set.of(Optional.ofNullable(request.role()).orElse(Role.USER).name()));

        Optional.of(codes)
            .filter(value -> !value.equals(Set.of("USER")))
            .ifPresent(value -> assignments.requireManagement());

        Optional.of(request.email())
            .map(Member::normalizeEmail)
            .filter(members::existsByEmail)
            .ifPresent(value -> {
                throw new PortalException(HttpStatus.CONFLICT, "이미 등록된 이메일입니다.");
            });

        Optional.of(request.password())
            .filter(value -> value.getBytes(StandardCharsets.UTF_8).length > 72)
            .ifPresent(value -> {
                throw new PortalException(HttpStatus.BAD_REQUEST, "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
            });

        var member = new Member(
            request.email(),
            request.name(),
            passwords.encode(request.password()),
            assignments.resolve(codes)
        );

        return MemberResponse.from(members.save(member));
    }

    @Transactional
    @PreAuthorize("hasAuthority('user:write')")
    public MemberResponse update(long id, MemberUpdateRequest request) {
        assignments.lock();
        rejectAmbiguousRoles(request.role(), request.roles());

        var member = require(id);

        Optional.of(member)
            .filter(value -> value.permissionCodes().contains("role:manage"))
            .ifPresent(value -> assignments.requireManagement());

        var codes = Optional.ofNullable(request.roles())
            .orElseGet(() -> Optional.ofNullable(request.role())
                .filter(value -> value != member.getRole())
                .map(value -> Set.of(value.name()))
                .orElseGet(() -> Set.copyOf(member.roleCodes())));

        Optional.of(codes)
            .filter(value -> !value.equals(Set.copyOf(member.roleCodes())))
            .ifPresent(value -> assignments.requireManagement());

        var replacement = assignments.resolve(codes);
        assignments.validateChange(member, replacement, request.status());
        member.replaceRoles(replacement);
        member.update(request.name(), request.status());

        return MemberResponse.from(member);
    }

    private void rejectAmbiguousRoles(Role role, Set<String> roles) {
        Optional.ofNullable(role)
            .filter(value -> roles != null)
            .ifPresent(value -> {
                throw new PortalException(HttpStatus.BAD_REQUEST, "role과 roles는 동시에 지정할 수 없습니다.");
            });
    }

    private Member require(long id) {
        return members.findById(id)
            .orElseThrow(() -> new PortalException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
