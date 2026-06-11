package com.example.solimus.repositories;

import com.example.solimus.entities.SubscriptionPayment;
import com.example.solimus.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {
    List<SubscriptionPayment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
    Optional<SubscriptionPayment> findByReference(String reference);

    /**
     * Trouve les paiements PENDING créés avant une date donnée (pour expiration).
     */
    List<SubscriptionPayment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime createdAt);
}
