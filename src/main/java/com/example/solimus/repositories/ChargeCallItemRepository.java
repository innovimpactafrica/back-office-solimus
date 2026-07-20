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
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChargeCallItemRepository extends JpaRepository<ChargeCallItem, Long> {

    /**
     * Récupère les charges courantes d'un copropriétaire
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

    // ===== CALCULS PAR COPROPRIÉTAIRE (LISTE COPROPRIÉTAIRES) =====

    /**
     * Compter les ChargeCallItems en retard pour un copropriétaire, restreint au syndic
     * Retard = dueDate < aujourd'hui ET status != PAID
     */
    @Query("SELECT COUNT(cci) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.syndic.id = :syndicId " +
           "AND cc.dueDate < CURRENT_DATE " +
           "AND cci.status != 'PAID'")
    long countLateItemsByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    /**
     * Calculer le solde global d'un copropriétaire, restreint au syndic
     * Solde = SUM(paidAmount) - SUM(quotePart)
     */
    @Query("SELECT COALESCE(SUM(cci.paidAmount), 0) - COALESCE(SUM(cci.quotePart), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.syndic.id = :syndicId")
    BigDecimal calculateSoldeByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    // ===== CALCULS POUR DÉTAIL COPROPRIÉTAIRE (KPIs) =====

    /**
     * Somme des paiements effectués par un copropriétaire, restreint au syndic
     */
    @Query("SELECT COALESCE(SUM(cci.paidAmount), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.syndic.id = :syndicId")
    BigDecimal sumPaymentsMadeByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    /**
     * Somme des impayés (quotePart - paidAmount) pour les lignes en retard, restreint au syndic
     * Retard = dueDate < aujourd'hui ET status != PAID
     */
    @Query("SELECT COALESCE(SUM(cci.quotePart - cci.paidAmount), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.syndic.id = :syndicId " +
           "AND cc.dueDate < CURRENT_DATE " +
           "AND cci.status != 'PAID'")
    BigDecimal sumUnpaidAmountByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    // ===== CALCULS PAR RÉSIDENCE POUR FINANCES COPROPRIÉTAIRE =====

    /**
     * Solde d'un copropriétaire pour une résidence
     * Solde = SUM(paidAmount) - SUM(quotePart)
     */
    @Query("SELECT COALESCE(SUM(cci.paidAmount), 0) - COALESCE(SUM(cci.quotePart), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.id = :residenceId")
    BigDecimal calculateSoldeByCoOwnerAndResidence(@Param("coOwnerId") Long coOwnerId, @Param("residenceId") Long residenceId);

    /**
     * Somme des paiements effectués par un copropriétaire pour une résidence et une année
     */
    @Query("SELECT COALESCE(SUM(cci.paidAmount), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.id = :residenceId " +
           "AND cc.year = :year")
    BigDecimal sumPaymentsMadeByCoOwnerAndResidence(@Param("coOwnerId") Long coOwnerId, @Param("residenceId") Long residenceId, @Param("year") Integer year);

    /**
     * Somme des quote-parts générées pour un copropriétaire, une résidence et une année
     */
    @Query("SELECT COALESCE(SUM(cci.quotePart), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.id = :residenceId " +
           "AND cc.year = :year")
    BigDecimal sumQuotePartGeneratedByCoOwnerAndResidenceAndYear(@Param("coOwnerId") Long coOwnerId, @Param("residenceId") Long residenceId, @Param("year") Integer year);

    // ===== MÉTHODES SUPPLÉMENTAIRES =====

    /**
     * Tous les items d'une résidence, toutes périodes confondues
     */
    @Query("SELECT cci FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId")
    List<ChargeCallItem> findByChargeCallBudgetResidenceId(@Param("residenceId") Long residenceId);

    /**
     * Items d'une résidence créés dans une période précise (via la date de création du ChargeCall parent)
     */
    @Query("SELECT cci FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId " +
           "AND cc.createdAt BETWEEN :start AND :end")
    List<ChargeCallItem> findByChargeCallBudgetResidenceIdAndChargeCallCreatedAtBetween(
            @Param("residenceId") Long residenceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Items du syndic dont le solde impayé dépasse un seuil donné
     */
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId " +
           "AND i.remainingAmount > :threshold")
    List<ChargeCallItem> findByChargeCallBudgetSyndicIdAndRemainingAmountGreaterThan(
            @Param("syndicId") Long syndicId, @Param("threshold") BigDecimal threshold);

    // Récupère le nombre de jours de retard le plus important parmi tous les ChargeCallItem non soldés
    // de ce copropriétaire, restreint aux résidences de ce syndic. Retourne null si aucun item en retard.
    @Query("SELECT MAX(DATEDIFF(CURRENT_DATE, i.chargeCall.dueDate)) FROM ChargeCallItem i " +
            "WHERE i.coOwner.id = :coOwnerId " +
            "AND i.chargeCall.budget.syndic.id = :syndicId " +
            "AND i.paidAmount < i.quotePart " +
            "AND i.chargeCall.dueDate < CURRENT_DATE")
    Integer findMaxDaysLateByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    // ===== PAIEMENTS / IMPAYÉS (GLOBAL SYNDIC) =====

    // Tous les items d'un syndic, paginés (pour l'onglet Paiements)
    Page<ChargeCallItem> findByChargeCallBudgetSyndicId(Long syndicId, Pageable pageable);

    // Items filtrés par nom de copropriétaire, paginés
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId " +
           "AND (LOWER(i.coOwner.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(i.coOwner.lastName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ChargeCallItem> findByChargeCallBudgetSyndicIdAndCoOwnerNameContaining(
            @Param("syndicId") Long syndicId, @Param("search") String search, Pageable pageable);

    // Items non soldés d'un syndic, paginés (pour l'onglet Impayés)
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId AND i.paidAmount < i.quotePart")
    Page<ChargeCallItem> findUnpaidByBudgetSyndicId(@Param("syndicId") Long syndicId, Pageable pageable);

    // Tous les items non soldés, sans pagination (pour calculer les KPI globaux : total, count)
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId AND i.paidAmount < i.quotePart")
    List<ChargeCallItem> findAllUnpaidByBudgetSyndicId(@Param("syndicId") Long syndicId);

    // Compte les lignes en retard (date d'échéance dépassée) et non soldées pour un syndic (toutes résidences)
    @Query("SELECT COUNT(i) FROM ChargeCallItem i " +
           "WHERE i.chargeCall.budget.syndic.id = :syndicId " +
           "AND i.paidAmount < i.quotePart " +
           "AND i.chargeCall.dueDate < CURRENT_DATE")
    long countLateUnpaidBySyndicId(@Param("syndicId") Long syndicId);

    // Additionne tout ce qui reste à payer pour ce copropriétaire, dans cette résidence,
    // toutes périodes de charge confondues (peu importe le statut de chaque ChargeCall)
    @Query("SELECT COALESCE(SUM(item.remainingAmount), 0) FROM ChargeCallItem item " +
           "WHERE item.coOwner.id = :coOwnerId " +
           "AND item.chargeCall.budget.residence.id = :residenceId " +
           "AND item.remainingAmount > 0")
    BigDecimal sumRemainingAmountByCoOwnerAndResidence(@Param("coOwnerId") Long coOwnerId,
                                                      @Param("residenceId") Long residenceId);

    // Charges en attente (remainingAmount > 0) de ce copropriétaire pour une résidence précise,
    // triées par échéance la plus proche en premier
    @Query("SELECT item FROM ChargeCallItem item " +
           "WHERE item.coOwner.id = :coOwnerId " +
           "AND item.chargeCall.budget.residence.id = :residenceId " +
           "AND item.remainingAmount > 0 " +
           "ORDER BY item.chargeCall.dueDate ASC")
    List<ChargeCallItem> findPendingItemsByCoOwnerAndResidence(@Param("coOwnerId") Long coOwnerId,
                                                              @Param("residenceId") Long residenceId,
                                                              Pageable pageable);

    // Supprimer tous les items d'un appel de charges
    void deleteByChargeCallId(Long chargeCallId);
}
