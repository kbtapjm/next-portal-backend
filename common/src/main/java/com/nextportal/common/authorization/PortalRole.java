package com.nextportal.common.authorization;

import jakarta.persistence.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "portal_role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortalRole {
    @Getter
    @Id @Column(length = 50)
    private String code;
    @Getter
    @Column(nullable = false, length = 100)
    private String name;
    @Getter
    @Column(name = "system_role", nullable = false)
    private boolean system;
    @Getter
    @Column(nullable = false)
    private long permissionRevision;
    @Version
    private long version;
    @ManyToMany
    @JoinTable(name = "portal_role_permission",
        joinColumns = @JoinColumn(name = "role_code"), inverseJoinColumns = @JoinColumn(name = "permission_code"))
    @BatchSize(size = 50)
    private Set<Permission> permissions = new HashSet<>();

    public PortalRole(String code, String name, Collection<Permission> permissions) {
        this.code = code;
        this.name = name.strip();
        this.permissions.addAll(permissions);
    }

    public Set<Permission> getPermissions() { return Set.copyOf(permissions); }
    public boolean hasPermission(String permission) { return permissions.stream().anyMatch(value -> value.getCode().equals(permission)); }

    public void update(String name, Collection<Permission> permissions) {
        var previous = this.permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
        var replacement = permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
        this.permissionRevision += previous.equals(replacement) ? 0 : 1;
        this.name = name.strip();
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }
}
