package com.nextportal.admin;

import com.nextportal.common.authorization.repository.RoleRepository;
import com.nextportal.common.member.entity.Member;
import com.nextportal.common.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final MemberRepository members;
    private final PasswordEncoder passwords;
    private final RoleRepository roles;
    private final String email;
    private final String password;
    public BootstrapAdmin(MemberRepository members, PasswordEncoder passwords, RoleRepository roles,
                          @Value("${portal.bootstrap-admin.email:}") String email,
                          @Value("${portal.bootstrap-admin.password:}") String password) {
        this.members = members; this.passwords = passwords; this.roles = roles; this.email = email; this.password = password;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        Optional.of(email).filter(value -> !value.isBlank()).filter(value -> !members.existsByEmail(Member.normalizeEmail(value)))
            .ifPresent(value -> {
                Optional.of(password).filter(secret -> secret.length() >= 12 && secret.getBytes(StandardCharsets.UTF_8).length <= 72)
                    .orElseThrow(() -> new IllegalArgumentException("Bootstrap password must be 12+ characters and at most 72 UTF-8 bytes"));
                members.save(new Member(value, "관리자", passwords.encode(password), List.of(roles.findById("ADMIN").orElseThrow())));
            });
    }
}
