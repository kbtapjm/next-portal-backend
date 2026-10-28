package com.nextportal.common.member.entity;

import com.nextportal.common.authorization.entity.PortalRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "portal_member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {
    @Getter
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Getter
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Getter
    @Column(nullable = false, length = 100)
    private String name;
    @Getter
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @ManyToMany
    @JoinTable(name = "portal_member_role",
        joinColumns = @JoinColumn(name = "member_id"), inverseJoinColumns = @JoinColumn(name = "role_code"))
    @BatchSize(size = 50)
    private Set<PortalRole> roles = new HashSet<>();
    @Getter
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MemberStatus status;
    @Getter
    @Column(nullable = false)
    private long authVersion;
    @Version
    private long version;
    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Member(String email, String name, String passwordHash, Collection<PortalRole> roles) {
        this.email = normalizeEmail(email);
        this.name = name.strip();
        this.passwordHash = passwordHash;
        this.roles.addAll(roles);
        this.status = MemberStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }

    public Set<PortalRole> getRoles() { return Set.copyOf(roles); }
    public List<String> roleCodes() { return roles.stream().map(PortalRole::getCode).sorted().toList(); }
    public List<String> permissionCodes() {
        return roles.stream().flatMap(role -> role.getPermissions().stream()).map(permission -> permission.getCode()).distinct().sorted().toList();
    }
    // Compatibility for the original frontend. Authorization never uses this two-value projection.
    public Role getRole() { return roles.stream().anyMatch(role -> role.hasPermission("admin:access")) ? Role.ADMIN : Role.USER; }

    public void update(String name, MemberStatus status) {
        this.authVersion += this.status != status ? 1 : 0;
        this.name = name.strip();
        this.status = status;
    }

    public void replaceRoles(Collection<PortalRole> replacement) {
        var codes = replacement.stream().map(PortalRole::getCode).collect(Collectors.toSet());
        this.authVersion += new HashSet<>(roleCodes()).equals(codes) ? 0 : 1;
        this.roles.clear();
        this.roles.addAll(replacement);
    }
}
