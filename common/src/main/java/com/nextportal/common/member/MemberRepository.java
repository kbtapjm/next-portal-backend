package com.nextportal.common.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByStatusAndRoles_Code(MemberStatus status, String code);
    boolean existsByRoles_Code(String code);
}
