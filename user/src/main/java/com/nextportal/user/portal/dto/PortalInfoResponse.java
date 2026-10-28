package com.nextportal.user.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PortalInfo", description = "포털 소개 정보")
public record PortalInfoResponse(
    String name,
    String description
) {
}
