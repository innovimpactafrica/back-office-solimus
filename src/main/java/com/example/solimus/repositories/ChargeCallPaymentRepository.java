package com.example.solimus.repositories;

import com.example.solimus.entities.ChargeCallPayment;
import com.example.solimus.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChargeCallPaymentRepository extends JpaRepository<ChargeCallPayment, Long> {

    Optional<ChargeCallPayment> findByReference(String reference);

    Optional<ChargeCallPayment> findByChargeCallItemId(Long chargeCallItemId);

    boolean existsByChargeCallItemIdAndStatus(Long chargeCallItemId, PaymentStatus status);

    /**
     * Trouve les paiements PENDING créés avant une date donnée (pour expiration).
     */
    List<ChargeCallPayment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime createdAt);
}
