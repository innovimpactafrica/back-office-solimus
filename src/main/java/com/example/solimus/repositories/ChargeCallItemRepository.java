package com.example.solimus.repositories;

import com.example.solimus.entities.ChargeCallItem;
import com.example.solimus.entities.User;
import com.example.solimus.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ChargeCallItemRepository extends JpaRepository<ChargeCallItem, Long> {

    /**
     * Lister toutes les lignes d'un appel de charges.
     */
    List<ChargeCallItem> findByChargeCallId(Long chargeCallId);

    /**
     * Lister toutes les lignes pour un copropriétaire.
     */
    List<ChargeCallItem> findByCoOwnerId(Long coOwnerId);

    /**
     * Lister toutes les lignes d'appel de charges pour une résidence
     * Triées du plus récent au plus ancien (par ChargeCall)
     */
    @Query("SELECT cci FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId " +
           "ORDER BY cc.year DESC, cc.periodNumber DESC")
    Page<ChargeCallItem> findByResidenceId(@Param("residenceId") Long residenceId, Pageable pageable);

    /**
     * Compter les lignes d'appel de charges pour un copropriétaire et une résidence.
     */
    @Query("SELECT COUNT(cci) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId AND cci.coOwner.id = :coOwnerId")
    long countByCoOwnerIdAndResidenceId(@Param("coOwnerId") Long coOwnerId, @Param("residenceId") Long residenceId);

    // ===== DASHBOARD SYNDIC - STATS GLOBALES =====

    /**
     * Somme des montants payés pour un syndic (trésorerie globale)
     */
    @Query("SELECT COALESCE(SUM(cci.paidAmount), 0) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.syndic = :syndic")
    BigDecimal sumPaidAmountBySyndic(@Param("syndic") User syndic);

    /**
     * Compter les résidences avec au moins un impayé pour un syndic
     */
    @Query("SELECT COUNT(DISTINCT b.residence.id) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.syndic = :syndic " +
           "AND cci.status != :paidStatus")
    long countResidencesWithUnpaidBySyndic(@Param("syndic") User syndic, @Param("paidStatus") PaymentStatus paidStatus);

    // ===== CALCULS PAR RÉSIDENCE =====

    /**
     * Somme des quote-parts pour une résidence (montant total dû)
     */
    @Query("SELECT COALESCE(SUM(cci.quotePart), 0) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId")
    BigDecimal sumQuotePartByResidenceId(@Param("residenceId") Long residenceId);

    /**
     * Somme des montants payés pour une résidence (trésorerie)
     */
    @Query("SELECT COALESCE(SUM(cci.paidAmount), 0) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId")
    BigDecimal sumPaidAmountByResidenceId(@Param("residenceId") Long residenceId);
}
