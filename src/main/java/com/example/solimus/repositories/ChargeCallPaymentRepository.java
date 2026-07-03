package com.example.solimus.repositories;

import com.example.solimus.entities.ChargeCallPayment;
import com.example.solimus.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChargeCallPaymentRepository extends JpaRepository<ChargeCallPayment, Long> {

    Optional<ChargeCallPayment> findByReference(String reference);

    Optional<ChargeCallPayment> findByChargeCallItemId(Long chargeCallItemId);

    /**
     * Trouve le dernier paiement COMPLETED pour un ChargeCallItem
     * Trié par date de paiement décroissante
     */
    @Query("SELECT p FROM ChargeCallPayment p " +
           "WHERE p.chargeCallItem.id = :chargeCallItemId " +
           "AND p.status = 'COMPLETED' " +
           "ORDER BY p.paidAt DESC")
    Optional<ChargeCallPayment> findLatestCompletedByChargeCallItemId(@Param("chargeCallItemId") Long chargeCallItemId);

    boolean existsByChargeCallItemIdAndStatus(Long chargeCallItemId, PaymentStatus status);

    /**
     * Trouve les paiements PENDING créés avant une date donnée (pour expiration).
     */
    List<ChargeCallPayment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime createdAt);

    /**
     * Somme des paiements COMPLETED par mois pour une résidence et une année
     * Groupe par mois (1-12) et somme les montants
     */
    @Query("SELECT MONTH(p.paidAt) as month, SUM(p.amount) as total " +
           "FROM ChargeCallPayment p " +
           "WHERE p.status = 'COMPLETED' " +
           "AND YEAR(p.paidAt) = :year " +
           "AND p.chargeCallItem.chargeCall.budget.residence.id = :residenceId " +
           "GROUP BY MONTH(p.paidAt) " +
           "ORDER BY MONTH(p.paidAt)")
    List<Object[]> sumCompletedPaymentsByMonth(
            @Param("residenceId") Long residenceId,
            @Param("year") Integer year);
}
