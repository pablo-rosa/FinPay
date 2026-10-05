package com.finpay.accounts.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class AccountExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account was not found", request);
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    ResponseEntity<ApiError> handleUnsupportedCurrency(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CURRENCY", "Currency code is not supported", request);
    }

    @ExceptionHandler(InvalidAccountStatusTransitionException.class)
    ResponseEntity<ApiError> handleInvalidStatus(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_ACCOUNT_STATUS_TRANSITION", "Account status cannot be changed to the requested value", request);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleConcurrentUpdate(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ACCOUNT_MODIFIED_CONCURRENTLY", "Account was modified concurrently; reload and retry", request);
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
