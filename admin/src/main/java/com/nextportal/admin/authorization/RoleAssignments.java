package com.nextportal.admin.authorization;

import com.nextportal.api.error.PortalException;
import com.nextportal.api.security.CurrentActor;
import com.nextportal.common.authorization.*;
import com.nextportal.common.member.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleAssignments {
    private final RoleRepository roles;
    private final MemberRepository members;

    public void lock() { roles.lockAdministration().orElseThrow(() -> new IllegalStateException("ADMIN role is missing")); }

    public List<PortalRole> resolve(Set<String> codes) {
        var found = roles.findAllById(codes);
        return Optional.of(found).filter(value -> value.size() == codes.size())
            .orElseThrow(() -> new PortalException(HttpStatus.BAD_REQUEST, "존재하지 않는 역할 코드가 포함되어 있습니다."));
    }

    public void requireManagement() {
        Optional.of(CurrentActor.has("role:manage")).filter(Boolean::booleanValue)
            .orElseThrow(() -> new AccessDeniedException("role:manage permission required"));
    }

    public void validateChange(Member member, Collection<PortalRole> replacement, MemberStatus status) {
        var codes = replacement.stream().map(PortalRole::getCode).sorted().toList();
        Optional.of(member).filter(value -> value.getId() == CurrentActor.id())
            .filter(value -> status != MemberStatus.ACTIVE || !value.roleCodes().equals(codes))
            .ifPresent(value -> { throw new PortalException(HttpStatus.CONFLICT, "본인을 비활성화하거나 본인 역할을 변경할 수 없습니다. 다른 권한 관리자에게 요청하세요."); });
        Optional.of(member).filter(value -> value.getStatus() == MemberStatus.ACTIVE && value.roleCodes().contains("ADMIN"))
            .filter(value -> status != MemberStatus.ACTIVE || !codes.contains("ADMIN"))
            .filter(value -> members.countByStatusAndRoles_Code(MemberStatus.ACTIVE, "ADMIN") <= 1)
            .ifPresent(value -> { throw new PortalException(HttpStatus.CONFLICT, "마지막 활성 시스템 관리자를 해제할 수 없습니다."); });
    }
}
