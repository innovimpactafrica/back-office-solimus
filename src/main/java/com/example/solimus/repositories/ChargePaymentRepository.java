package com.example.solimus.repositories;

import com.example.solimus.entities.ChargePayment;
import com.example.solimus.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChargePaymentRepository extends JpaRepository<ChargePayment, Long> {

    Optional<ChargePayment> findByReference(String reference);

    Optional<ChargePayment> findByAllocationId(Long allocationId);

    boolean existsByAllocationIdAndStatus(Long allocationId, PaymentStatus status);

    /**
     * Trouve les paiements PENDING créés avant une date donnée (pour expiration).
     */
    List<ChargePayment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime createdAt);
}
