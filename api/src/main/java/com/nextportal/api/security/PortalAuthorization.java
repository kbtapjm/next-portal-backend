package com.nextportal.api.security;

import com.nextportal.common.authorization.Permission;
import com.nextportal.common.authorization.PortalRole;
import com.nextportal.common.authorization.RoleRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalAuthorization {
    private final RoleRepository roles;

    public Snapshot forMember(long memberId) {
        var assigned = roles.findForMember(memberId);
        var codes = assigned.stream().map(PortalRole::getCode).sorted().toList();
        var permissions = assigned.stream().flatMap(role -> role.getPermissions().stream())
            .map(Permission::getCode).distinct().sorted().toList();
        var revisions = assigned.stream().map(role -> role.getCode() + ":" + role.getPermissionRevision())
            .sorted().collect(Collectors.joining("|"));
        return new Snapshot(codes, permissions, JwtTokens.digest(revisions));
    }

    public record Snapshot(List<String> roles, List<String> permissions, String version) {
        public boolean canAccessAdmin() { return permissions.contains("admin:access"); }
    }
}
