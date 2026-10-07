package com.finpay.demo.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class DemoExceptionHandler {

    @ExceptionHandler(DemoRateLimitException.class)
    ResponseEntity<ApiError> handleRateLimit(DemoRateLimitException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ApiError(
                Instant.now(), 429, "DEMO_RATE_LIMITED", exception.getMessage(), request.getRequestURI()
        ));
    }

    public record ApiError(Instant timestamp, int status, String code, String message, String path) { }
}
