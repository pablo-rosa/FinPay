package com.finpay.demo.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "finpay.demo.enabled", havingValue = "true")
public class DemoSessionRateLimiter {

    private final Map<String, Window> windows = new HashMap<>();
    private final int maxSessions;
    private final Duration windowDuration;

    public DemoSessionRateLimiter(
            @Value("${finpay.demo.rate-limit.max-sessions:10}") int maxSessions,
            @Value("${finpay.demo.rate-limit.window:PT1H}") Duration windowDuration
    ) {
        if (maxSessions < 1 || windowDuration.isNegative() || windowDuration.isZero()) {
            throw new IllegalArgumentException("Demo rate limit must use a positive count and duration");
        }
        this.maxSessions = maxSessions;
        this.windowDuration = windowDuration;
    }

    public synchronized boolean allow(String clientAddress) {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt().plus(windowDuration).isBefore(now));
        Window current = windows.get(clientAddress);
        if (current == null || current.startedAt().plus(windowDuration).isBefore(now)) {
            windows.put(clientAddress, new Window(now, 1));
            return true;
        }
        if (current.count() >= maxSessions) {
            return false;
        }
        windows.put(clientAddress, new Window(current.startedAt(), current.count() + 1));
        return true;
    }

    private record Window(Instant startedAt, int count) { }
}
