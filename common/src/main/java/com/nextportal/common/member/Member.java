package com.nextportal.common.member;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    @Getter
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role;
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

    public Member(String email, String name, String passwordHash, Role role) {
        this.email = normalizeEmail(email);
        this.name = name.strip();
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = MemberStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }

    public void update(String name, Role role, MemberStatus status) {
        this.authVersion += (this.role != role || this.status != status) ? 1 : 0;
        this.name = name.strip();
        this.role = role;
        this.status = status;
    }
}
