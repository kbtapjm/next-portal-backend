package com.nextportal.api.error.dto;

public record FieldErrorResponse(
    String field,
    String message
) {
}
