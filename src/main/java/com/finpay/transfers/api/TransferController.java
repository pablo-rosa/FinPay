package com.finpay.transfers.api;

import com.finpay.transfers.application.TransferService;
import com.finpay.transfers.domain.Transfer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody CreateTransferRequest request) {
        Transfer transfer = transferService.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/transfers/" + transfer.getId()))
                .body(TransferResponse.from(transfer));
    }

    @GetMapping
    public List<TransferResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return transferService.list(userId(jwt)).stream().map(TransferResponse::from).toList();
    }

    @GetMapping("/{transferId}")
    public TransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transferId) {
        return TransferResponse.from(transferService.get(userId(jwt), transferId));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
