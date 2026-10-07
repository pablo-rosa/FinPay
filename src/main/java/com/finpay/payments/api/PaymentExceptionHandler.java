package com.finpay.payments.api;

import com.finpay.payments.application.PaymentException;
import com.finpay.payments.domain.PaymentStateException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    ResponseEntity<ApiError> handlePaymentException(PaymentException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiError(
                Instant.now(), exception.getStatus().value(), exception.getCode(), exception.getMessage(), request.getRequestURI()
        ));
    }

    @ExceptionHandler(PaymentStateException.class)
    ResponseEntity<ApiError> handleStateException(PaymentStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                Instant.now(), HttpStatus.CONFLICT.value(), "INVALID_PAYMENT_STATE_TRANSITION", exception.getMessage(), request.getRequestURI()
        ));
    }

    public record ApiError(Instant timestamp, int status, String code, String message, String path) {
    }
}
