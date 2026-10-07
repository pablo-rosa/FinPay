package com.finpay.demo.api;

public class DemoRateLimitException extends RuntimeException {
    public DemoRateLimitException() {
        super("Demo sessions are temporarily limited. Please try again later.");
    }
}
