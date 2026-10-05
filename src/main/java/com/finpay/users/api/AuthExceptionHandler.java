package com.finpay.users.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ApiError> handleDuplicateEmail(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account with this email already exists", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleInvalidCredentials(HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect", request);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    ResponseEntity<ApiError> handleInvalidPassword(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password exceeds the supported length", request);
    }

    private static ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), code, message, request.getRequestURI()
        ));
    }

    public record ApiError(Instant timestamp, int status, String code, String message, String path) {
    }
}
