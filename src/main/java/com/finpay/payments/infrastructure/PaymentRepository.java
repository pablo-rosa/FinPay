package com.finpay.payments.infrastructure;

import com.finpay.payments.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Payment> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id and payment.userId = :userId")
    Optional<Payment> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
