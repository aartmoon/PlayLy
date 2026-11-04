package com.playly.core.web;

import com.playly.core.storage.GameStorage;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Map;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handle(Exception error) {
        ApiProblem problem;
        if (error instanceof ApiProblem apiProblem) {
            problem = apiProblem;
        } else if (error instanceof GameStorage.StorageUnavailable) {
            LoggerFactory.getLogger(ApiErrors.class).warn("Could not issue a game package", error);
            problem = new ApiProblem(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Game storage is unavailable");
        } else if (error instanceof HttpMessageNotReadableException
                || error instanceof HandlerMethodValidationException
                || error instanceof ConstraintViolationException) {
            problem = ApiProblem.badRequest();
        } else if (error instanceof ErrorResponse response
                && response.getStatusCode() instanceof HttpStatus status && status.is4xxClientError()) {
            problem = new ApiProblem(status, status.name(), status.getReasonPhrase());
        } else {
            LoggerFactory.getLogger(ApiErrors.class).error("Unhandled API error", error);
            problem = new ApiProblem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
        }
        var response = ResponseEntity.status(problem.status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (problem.retryAfter != null) {
            response.header("Retry-After", problem.retryAfter.toString());
        }
        return response.body(problem.body());
    }
}
