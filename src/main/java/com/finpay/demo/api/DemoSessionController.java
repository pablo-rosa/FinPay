package com.finpay.demo.api;

import com.finpay.demo.application.DemoSessionService;
import com.finpay.demo.infrastructure.DemoSessionRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "finpay.demo.enabled", havingValue = "true")
public class DemoSessionController {

    private final DemoSessionService sessions;
    private final DemoSessionRateLimiter rateLimiter;

    public DemoSessionController(DemoSessionService sessions, DemoSessionRateLimiter rateLimiter) {
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/session")
    public ResponseEntity<DemoSessionResponse> createSession(HttpServletRequest request) {
        String clientAddress = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (!rateLimiter.allow(clientAddress)) {
            throw new DemoRateLimitException();
        }
        return ResponseEntity.created(URI.create("/api/demo/session"))
                .body(sessions.createSession());
    }
}
