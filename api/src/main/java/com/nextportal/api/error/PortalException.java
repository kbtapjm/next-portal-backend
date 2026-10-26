package com.nextportal.api.error;

import org.springframework.http.HttpStatus;

public class PortalException extends RuntimeException {
    private final HttpStatus status;
    public PortalException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
