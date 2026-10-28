package com.nextportal.common.member.repository;

import com.nextportal.common.member.entity.Member;
import com.nextportal.common.member.entity.MemberStatus;
import com.nextportal.common.member.entity.QMember;
import com.nextportal.common.member.entity.Role;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class MemberQueries {
    private final JPAQueryFactory query;
    public MemberQueries(EntityManager entityManager) { query = new JPAQueryFactory(entityManager); }

    public Page<Member> search(String keyword, Role role, String roleCode, MemberStatus status, int page, int size) {
        var member = QMember.member;
        var where = new BooleanBuilder();
        Optional.ofNullable(keyword).map(String::strip).filter(value -> !value.isBlank())
            .ifPresent(value -> where.and(member.email.containsIgnoreCase(value)
                .or(member.name.containsIgnoreCase(value))));
        Optional.ofNullable(role).ifPresent(value -> where.and(value == Role.ADMIN
            ? member.roles.any().permissions.any().code.eq("admin:access")
            : member.roles.any().permissions.any().code.eq("admin:access").not()));
        Optional.ofNullable(roleCode).ifPresent(value -> where.and(member.roles.any().code.eq(value)));
        Optional.ofNullable(status).ifPresent(value -> where.and(member.status.eq(value)));
        var pageable = PageRequest.of(page, size);
        var rows = query.selectFrom(member).where(where).orderBy(member.id.desc())
            .offset(pageable.getOffset()).limit(size).fetch();
        var total = Optional.ofNullable(query.select(member.count()).from(member).where(where).fetchOne()).orElse(0L);
        return new PageImpl<>(rows, pageable, total);
    }
}
