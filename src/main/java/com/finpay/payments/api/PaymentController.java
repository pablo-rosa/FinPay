package com.finpay.payments.api;

import com.finpay.payments.application.PaymentCreationResult;
import com.finpay.payments.application.PaymentService;
import com.finpay.payments.domain.Payment;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentCreationResult result = paymentService.create(userId(jwt), idempotencyKey, request);
        Payment payment = result.payment();
        var response = PaymentResponse.from(payment);
        if (result.replayed()) {
            return ResponseEntity.ok().location(URI.create("/api/payments/" + payment.getId())).body(response);
        }
        return ResponseEntity.created(URI.create("/api/payments/" + payment.getId())).body(response);
    }

    @PostMapping("/{paymentId}/submit")
    public PaymentResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.submit(userId(jwt), paymentId));
    }

    @PostMapping("/{paymentId}/authorize")
    public PaymentResponse authorize(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.authorize(userId(jwt), paymentId));
    }

    @PostMapping("/{paymentId}/reject")
    public PaymentResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.reject(userId(jwt), paymentId));
    }

    @PostMapping("/{paymentId}/capture")
    public PaymentResponse capture(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.capture(userId(jwt), paymentId));
    }

    @PostMapping("/{paymentId}/complete")
    public PaymentResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.complete(userId(jwt), paymentId));
    }

    @GetMapping
    public List<PaymentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return paymentService.list(userId(jwt)).stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.get(userId(jwt), paymentId));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
