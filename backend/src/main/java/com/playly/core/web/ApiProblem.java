package com.playly.core.web;

import org.springframework.http.HttpStatus;

import java.util.Map;

public final class ApiProblem extends RuntimeException {
    final HttpStatus status;
    final String code;
    final Integer retryAfter;

    public ApiProblem(HttpStatus status, String code, String detail) {
        this(status, code, detail, null);
    }

    public ApiProblem(HttpStatus status, String code, String detail, Integer retryAfter) {
        super(detail);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public static ApiProblem missing() {
        return new ApiProblem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    public static ApiProblem badRequest() {
        return new ApiProblem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request parameters or body");
    }

    public static ApiProblem conflict(String code, String detail) {
        return new ApiProblem(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiProblem unauthorized() {
        return new ApiProblem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "A valid access token is required");
    }

    public Map<String, Object> body() {
        return Map.of("type", "about:blank", "title", status.getReasonPhrase(),
                "status", status.value(), "code", code, "detail", getMessage());
    }
}
