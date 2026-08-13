package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicWithdrawalRequest;
import com.example.solimus.enums.WalletTransactionCategory;
import com.example.solimus.enums.WithdrawalStatus;
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
public interface SyndicWithdrawalRequestRepository extends JpaRepository<SyndicWithdrawalRequest, Long> {


    /** Somme des retraits COMPLETED (validés) liés à un poste budgétaire précis.
      Pas besoin de filtrer par résidence/année en plus : chaque BudgetItem appartient
      à un seul Budget (lui-même unique par résidence+année), donc budgetItemId identifie
      déjà, à lui seul, une résidence et une année précises — même si deux postes de
      résidences différentes portent le même libellé (ex: "Assurance"), leurs ID restent différents. */

    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM SyndicWithdrawalRequest w " +
            "WHERE w.budgetItem.id = :budgetItemId " +
            "AND w.status = 'COMPLETED'")
    BigDecimal sumCompletedByBudgetItem(@Param("budgetItemId") Long budgetItemId);



    // Additionne les demandes de retrait en attente sur une période donnée, optionnellement filtré par résidence
    // Utilisée pour le KPI "Retraits en attente" (statut PENDING, période = mois en cours)
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM SyndicWithdrawalRequest w " +
           "WHERE w.wallet.id = :walletId AND w.status = 'PENDING' " +
           "AND w.requestedAt BETWEEN :startDate AND :endDate " +
           "AND (:residenceId IS NULL OR w.residence.id = :residenceId)")
    BigDecimal sumPendingAmountByPeriod(@Param("walletId") Long walletId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate,
                                          @Param("residenceId") Long residenceId);

    // Somme des demandes PENDING, sans limite de période, optionnellement filtré par résidence
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM SyndicWithdrawalRequest w " +
           "WHERE w.wallet.id = :walletId AND w.status = 'PENDING' " +
           "AND (:residenceId IS NULL OR w.residence.id = :residenceId)")
    BigDecimal sumPendingAmount(@Param("walletId") Long walletId, @Param("residenceId") Long residenceId);

    // Somme des demandes COMPLETED (retraits réellement effectués), depuis toujours, optionnellement filtré par résidence
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM SyndicWithdrawalRequest w " +
           "WHERE w.wallet.id = :walletId AND w.status = 'COMPLETED' " +
           "AND (:residenceId IS NULL OR w.residence.id = :residenceId)")
    BigDecimal sumCompletedAmount(@Param("walletId") Long walletId, @Param("residenceId") Long residenceId);

    // Somme des retraits COMPLETED traités jusqu'à une date précise (pas "depuis toujours") — pour
    // calculer la trésorerie disponible à une date passée (ex: un point du graphique mensuel)
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM SyndicWithdrawalRequest w " +
           "WHERE w.wallet.id = :walletId AND w.status = 'COMPLETED' " +
           "AND w.processedAt <= :asOfDate " +
           "AND (:residenceId IS NULL OR w.residence.id = :residenceId)")
    BigDecimal sumCompletedAmountUpTo(@Param("walletId") Long walletId,
                                       @Param("asOfDate") LocalDateTime asOfDate,
                                       @Param("residenceId") Long residenceId);

    // Historique paginé des demandes de retrait, triées par date décroissante, optionnellement filtré par résidence
    @Query("SELECT w FROM SyndicWithdrawalRequest w " +
           "WHERE w.wallet.id = :walletId " +
           "AND (:residenceId IS NULL OR w.residence.id = :residenceId) " +
           "ORDER BY w.requestedAt DESC")
    Page<SyndicWithdrawalRequest> findByWalletId(@Param("walletId") Long walletId,
                                                 @Param("residenceId") Long residenceId,
                                                 Pageable pageable);

    // ============================================================
    // ADMIN — DEMANDES DE RETRAIT (toutes syndics confondus)
    // ============================================================

    // Compte toutes les demandes ayant ce statut, tous syndics confondus
    long countByStatus(WithdrawalStatus status);

    // Compte les demandes créées dans une période donnée, tous statuts — "Total Demandes" (évolution)
    long countByRequestedAtBetween(LocalDateTime start, LocalDateTime end);

    // Compte les demandes ayant ce statut ET créées dans une période donnée — "Demande Validée" (évolution)
    long countByStatusAndRequestedAtBetween(WithdrawalStatus status, LocalDateTime start, LocalDateTime end);

    // Compte les demandes ayant ce statut ET traitées (processedAt) dans une période donnée —
    // "Demande refusée" (fenêtre des 30 derniers jours)
    long countByStatusAndProcessedAtBetween(WithdrawalStatus status, LocalDateTime start, LocalDateTime end);

    // Somme de toutes les demandes ayant ce statut, sans limite de période — "Montant Total" (COMPLETED)
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM SyndicWithdrawalRequest w WHERE w.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") WithdrawalStatus status);

    // Somme des demandes COMPLETED d'un wallet précis, sur une période donnée — "Retiré ce mois" (Bloc 3)
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM SyndicWithdrawalRequest w " +
           "WHERE w.wallet.id = :walletId AND w.status = 'COMPLETED' " +
           "AND w.processedAt BETWEEN :start AND :end")
    BigDecimal sumCompletedAmountInPeriod(@Param("walletId") Long walletId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    // Les N dernières demandes COMPLETED de ce syndic, en excluant la demande actuellement consultée —
    // "Derniers retraits effectués" (Bloc 3)
    List<SyndicWithdrawalRequest> findByWallet_Syndic_IdAndStatusAndIdNotOrderByProcessedAtDesc(
            Long syndicId, WithdrawalStatus status, Long excludedId, Pageable pageable);
}
