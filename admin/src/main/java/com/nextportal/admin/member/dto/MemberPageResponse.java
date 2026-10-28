package com.nextportal.admin.member.dto;

import com.nextportal.common.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(name = "MemberPage", description = "사용자 검색 결과. page는 0부터 시작합니다.")
public record MemberPageResponse(
    List<MemberResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public static MemberPageResponse from(Page<Member> page) {
        return new MemberPageResponse(
            page.getContent().stream().map(MemberResponse::from).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
