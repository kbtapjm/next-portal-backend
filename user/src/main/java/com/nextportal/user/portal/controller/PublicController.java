package com.nextportal.user.portal.controller;

import com.nextportal.user.portal.dto.PortalInfoResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "사용자 포털", description = "사용자용 공개 API")
public class PublicController {

    @Operation(
        operationId = "publicInfo",
        summary = "포털 기본 정보 조회",
        description = "인증 없이 호출할 수 있는 포털 소개 정보입니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "포털 소개 정보",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = PortalInfoResponse.class)
        )
    )
    @GetMapping("/api/public/info")
    public PortalInfoResponse info() {
        return new PortalInfoResponse("Next Portal", "데이터 포털 사용자 API 준비 중");
    }
}
