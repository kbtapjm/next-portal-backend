package com.nextportal.admin.authorization;

import com.nextportal.admin.member.MemberModels;
import com.nextportal.api.error.PortalException;
import com.nextportal.api.security.CurrentActor;
import com.nextportal.common.authorization.*;
import com.nextportal.common.member.MemberRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nextportal.admin.authorization.RoleModels.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("denyAll()")
public class RoleService {
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final MemberRepository members;
    private final RoleAssignments assignments;

    @PreAuthorize("hasAuthority('role:read')")
    public List<PermissionView> permissions() { return permissions.findAllByOrderByCodeAsc().stream().map(PermissionView::from).toList(); }

    @PreAuthorize("hasAuthority('role:read')")
    public List<RoleView> roles() { return roles.findAllByOrderByCodeAsc().stream().map(RoleView::from).toList(); }

    @PreAuthorize("hasAuthority('role:read')")
    public RoleView get(String code) { return RoleView.from(require(code)); }

    @Transactional
    @PreAuthorize("hasAuthority('role:manage')")
    public RoleView create(CreateRole request) {
        assignments.lock();
        Optional.of(request.code()).filter(roles::existsById)
            .ifPresent(value -> { throw new PortalException(HttpStatus.CONFLICT, "이미 존재하는 역할 코드입니다."); });
        return RoleView.from(roles.save(new PortalRole(request.code(), request.name(), resolvePermissions(request.permissions()))));
    }

    @Transactional
    @PreAuthorize("hasAuthority('role:manage')")
    public RoleView update(String code, UpdateRole request) {
        assignments.lock();
        var role = requireEditable(code);
        var replacement = resolvePermissions(request.permissions());
        var previous = role.getPermissions().stream().map(Permission::getCode).collect(java.util.stream.Collectors.toSet());
        Optional.of(previous).filter(value -> !value.equals(request.permissions()))
            .filter(value -> members.findById(CurrentActor.id()).orElseThrow().roleCodes().contains(code))
            .ifPresent(value -> { throw new PortalException(HttpStatus.CONFLICT, "본인에게 부여된 역할의 권한은 다른 권한 관리자가 변경해야 합니다."); });
        role.update(request.name(), replacement);
        return RoleView.from(role);
    }

    @Transactional
    @PreAuthorize("hasAuthority('role:manage')")
    public void delete(String code) {
        assignments.lock();
        var role = requireEditable(code);
        Optional.of(code).filter(members::existsByRoles_Code)
            .ifPresent(value -> { throw new PortalException(HttpStatus.CONFLICT, "사용자에게 부여된 역할은 삭제할 수 없습니다. 역할을 먼저 회수하세요."); });
        roles.delete(role);
    }

    @Transactional
    @PreAuthorize("hasAuthority('role:manage')")
    public MemberModels.View assign(long memberId, AssignRoles request) {
        assignments.lock();
        var member = members.findById(memberId).orElseThrow(() -> new PortalException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        var replacement = assignments.resolve(request.roles());
        assignments.validateChange(member, replacement, member.getStatus());
        member.replaceRoles(replacement);
        return MemberModels.View.from(member);
    }

    private PortalRole require(String code) {
        return roles.findById(code).orElseThrow(() -> new PortalException(HttpStatus.NOT_FOUND, "역할을 찾을 수 없습니다."));
    }

    private PortalRole requireEditable(String code) {
        return Optional.of(require(code)).filter(value -> !value.isSystem())
            .orElseThrow(() -> new PortalException(HttpStatus.CONFLICT, "시스템 역할 ADMIN·USER는 변경하거나 삭제할 수 없습니다."));
    }

    private List<Permission> resolvePermissions(Set<String> codes) {
        var found = permissions.findAllById(codes);
        return Optional.of(found).filter(value -> value.size() == codes.size())
            .orElseThrow(() -> new PortalException(HttpStatus.BAD_REQUEST, "존재하지 않는 권한 코드가 포함되어 있습니다."));
    }
}
