package com.finpay.transfers.api;

import com.finpay.transfers.domain.TransferException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class TransferExceptionHandler {

    @ExceptionHandler(TransferException.class)
    ResponseEntity<ApiError> handleTransferException(TransferException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiError(
                Instant.now(), exception.getStatus().value(), exception.getCode(), exception.getMessage(), request.getRequestURI()
        ));
    }

    public record ApiError(Instant timestamp, int status, String code, String message, String path) {
    }
}
