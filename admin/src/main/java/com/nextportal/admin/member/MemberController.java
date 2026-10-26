package com.nextportal.admin.member;

import com.nextportal.common.member.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.nextportal.admin.member.MemberModels.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@Validated
public class MemberController {
    private final MemberService service;
    @GetMapping
    PageView search(@RequestParam(required = false) @Size(max = 100) String keyword,
                    @RequestParam(required = false) Role role, @RequestParam(required = false) MemberStatus status,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(keyword, role, status, page, size);
    }
    @GetMapping("/{id}") View get(@PathVariable @Positive long id) { return service.get(id); }
    @PostMapping ResponseEntity<View> create(@Valid @RequestBody Create request) {
        var member = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/users/" + member.id())).body(member);
    }
    @PutMapping("/{id}") View update(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
                                    @Valid @RequestBody Update request) {
        return service.update(Long.parseLong(jwt.getSubject()), id, request);
    }
}
