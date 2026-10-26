package com.nextportal.admin.member;

import com.nextportal.api.error.PortalException;
import com.nextportal.common.member.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nextportal.admin.member.MemberModels.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository members;
    private final MemberQueries queries;
    private final PasswordEncoder passwords;
    public PageView search(String keyword, Role role, MemberStatus status, int page, int size) {
        return PageView.from(queries.search(keyword, role, status, page, size));
    }
    public View get(long id) { return View.from(require(id)); }
    @Transactional
    public View create(Create request) {
        Optional.of(request.email()).map(Member::normalizeEmail).filter(members::existsByEmail).ifPresent(value -> {
            throw new PortalException(HttpStatus.CONFLICT, "이미 등록된 이메일입니다.");
        });
        Optional.of(request.password()).filter(value -> value.getBytes(StandardCharsets.UTF_8).length > 72).ifPresent(value -> {
            throw new PortalException(HttpStatus.BAD_REQUEST, "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        });
        return View.from(members.save(new Member(request.email(), request.name(), passwords.encode(request.password()), request.role())));
    }
    @Transactional
    public View update(long actorId, long id, Update request) {
        Optional.of(id).filter(value -> value == actorId)
            .filter(value -> request.role() != Role.ADMIN || request.status() != MemberStatus.ACTIVE)
            .ifPresent(value -> { throw new PortalException(HttpStatus.CONFLICT, "본인 관리자 권한을 해제하거나 비활성화할 수 없습니다."); });
        var member = require(id);
        member.update(request.name(), request.role(), request.status());
        return View.from(member);
    }
    private Member require(long id) {
        return members.findById(id).orElseThrow(() -> new PortalException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
