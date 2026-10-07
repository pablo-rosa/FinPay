package com.finpay.transfers.domain;

import org.springframework.http.HttpStatus;

public class TransferException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public TransferException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
