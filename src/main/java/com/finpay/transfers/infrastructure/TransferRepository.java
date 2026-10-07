package com.finpay.transfers.infrastructure;

import com.finpay.transfers.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    List<Transfer> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Transfer> findByIdAndUserId(UUID id, UUID userId);
}
