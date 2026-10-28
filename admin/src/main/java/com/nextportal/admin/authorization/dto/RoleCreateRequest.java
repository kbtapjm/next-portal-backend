package com.nextportal.admin.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(name = "CreateRole")
public record RoleCreateRequest(
    @Schema(example = "AUDITOR")
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}")
    String code,

    @Schema(example = "감사 담당자")
    @NotBlank
    @Size(max = 100)
    String name,

    @Schema(example = "[\"admin:access\",\"user:read\"]")
    @NotNull
    @Size(max = 100)
    Set<@NotBlank @Size(max = 80) String> permissions
) {
}
