package com.example.solimus.repositories;

import com.example.solimus.entities.ChargeCallPayment;
import com.example.solimus.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<ChargeCallPayment> findFirstByChargeCallItemIdOrderByPaidAtDesc(Long chargeCallItemId);

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

    /**
     * Somme des paiements COMPLETED par mois pour un copropriétaire, une résidence et une année
     */
    @Query("SELECT MONTH(p.paidAt) as month, SUM(p.amount) as total " +
           "FROM ChargeCallPayment p " +
           "WHERE p.status = 'COMPLETED' " +
           "AND YEAR(p.paidAt) = :year " +
           "AND p.chargeCallItem.chargeCall.budget.residence.id = :residenceId " +
           "AND p.chargeCallItem.coOwner.id = :coOwnerId " +
           "GROUP BY MONTH(p.paidAt) " +
           "ORDER BY MONTH(p.paidAt)")
    List<Object[]> sumCompletedPaymentsByMonthForCoOwner(
            @Param("coOwnerId") Long coOwnerId,
            @Param("residenceId") Long residenceId,
            @Param("year") Integer year);

    /**
     * Paginer les paiements d'un copropriétaire, restreint au syndic connecté, avec filtre de statut
     */
    @Query("SELECT p FROM ChargeCallPayment p " +
           "WHERE p.chargeCallItem.coOwner.id = :coOwnerId " +
           "AND p.chargeCallItem.chargeCall.budget.residence.syndic.id = :syndicId " +
           "AND (:status IS NULL OR p.status = :status) " +
           "ORDER BY COALESCE(p.paidAt, p.createdAt) DESC")
    Page<ChargeCallPayment> findByCoOwnerAndSyndicAndStatus(
            @Param("coOwnerId") Long coOwnerId,
            @Param("syndicId") Long syndicId,
            @Param("status") String status,
            Pageable pageable);

    // Somme des paiements de charges d'un syndic, reçus dans une période donnée
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM ChargeCallPayment p " +
            "WHERE p.chargeCallItem.chargeCall.budget.syndic.id = :syndicId " +
            "AND p.status = 'COMPLETED' " +
            "AND p.paidAt >= :start AND p.paidAt < :end")
    BigDecimal sumByBudgetSyndicIdAndPaidAtBetween(
            @Param("syndicId") Long syndicId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
