package com.nextportal.admin.member;

import com.nextportal.api.error.PortalException;
import com.nextportal.api.security.CurrentActor;
import com.nextportal.admin.authorization.RoleAssignments;
import com.nextportal.common.member.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nextportal.admin.member.MemberModels.*;

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
    public PageView search(String keyword, Role role, String roleCode, MemberStatus status, int page, int size) {
        return PageView.from(queries.search(keyword, role, roleCode, status, page, size));
    }
    @PreAuthorize("hasAuthority('user:read')")
    public View get(long id) { return View.from(require(id)); }
    @PreAuthorize("hasAuthority('admin:access')")
    public View current() { return View.from(require(CurrentActor.id())); }
    @Transactional
    @PreAuthorize("hasAuthority('user:write')")
    public View create(Create request) {
        assignments.lock();
        rejectAmbiguousRoles(request.role(), request.roles());
        var codes = Optional.ofNullable(request.roles()).orElseGet(() -> Set.of(Optional.ofNullable(request.role()).orElse(Role.USER).name()));
        Optional.of(codes).filter(value -> !value.equals(Set.of("USER"))).ifPresent(value -> assignments.requireManagement());
        Optional.of(request.email()).map(Member::normalizeEmail).filter(members::existsByEmail).ifPresent(value -> {
            throw new PortalException(HttpStatus.CONFLICT, "이미 등록된 이메일입니다.");
        });
        Optional.of(request.password()).filter(value -> value.getBytes(StandardCharsets.UTF_8).length > 72).ifPresent(value -> {
            throw new PortalException(HttpStatus.BAD_REQUEST, "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        });
        return View.from(members.save(new Member(request.email(), request.name(), passwords.encode(request.password()), assignments.resolve(codes))));
    }
    @Transactional
    @PreAuthorize("hasAuthority('user:write')")
    public View update(long id, Update request) {
        assignments.lock();
        rejectAmbiguousRoles(request.role(), request.roles());
        var member = require(id);
        Optional.of(member).filter(value -> value.permissionCodes().contains("role:manage")).ifPresent(value -> assignments.requireManagement());
        var codes = Optional.ofNullable(request.roles()).orElseGet(() -> Optional.ofNullable(request.role())
            .filter(value -> value != member.getRole()).map(value -> Set.of(value.name())).orElseGet(() -> Set.copyOf(member.roleCodes())));
        Optional.of(codes).filter(value -> !value.equals(Set.copyOf(member.roleCodes()))).ifPresent(value -> assignments.requireManagement());
        var replacement = assignments.resolve(codes);
        assignments.validateChange(member, replacement, request.status());
        member.replaceRoles(replacement);
        member.update(request.name(), request.status());
        return View.from(member);
    }
    private void rejectAmbiguousRoles(Role role, Set<String> roles) {
        Optional.ofNullable(role).filter(value -> roles != null)
            .ifPresent(value -> { throw new PortalException(HttpStatus.BAD_REQUEST, "role과 roles는 동시에 지정할 수 없습니다."); });
    }
    private Member require(long id) {
        return members.findById(id).orElseThrow(() -> new PortalException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
