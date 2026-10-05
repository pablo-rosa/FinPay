package com.finpay.ledger.application;

public class LedgerPostingException extends RuntimeException {
    private final String code;

    public LedgerPostingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
