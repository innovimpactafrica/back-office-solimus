package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicWithdrawalRequest;
import com.example.solimus.enums.WalletTransactionCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface SyndicWithdrawalRequestRepository extends JpaRepository<SyndicWithdrawalRequest, Long> {

    /**
     * Somme des retraits en cours (PENDING) ou validés (COMPLETED) pour un wallet
     * Utilisé pour calculer la trésorerie disponible réelle (transactions - retraits en attente)
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM SyndicWithdrawalRequest r " +
           "WHERE r.wallet.id = :walletId AND r.status IN (com.example.solimus.enums.WithdrawalStatus.PENDING, com.example.solimus.enums.WithdrawalStatus.COMPLETED)")
    BigDecimal sumPendingAndValidatedByWallet(@Param("walletId") Long walletId);

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

    // Historique paginé des demandes de retrait, triées par date décroissante, optionnellement filtré par résidence
    @Query("SELECT w FROM SyndicWithdrawalRequest w " +
           "WHERE w.wallet.id = :walletId " +
           "AND (:residenceId IS NULL OR w.residence.id = :residenceId) " +
           "ORDER BY w.requestedAt DESC")
    Page<SyndicWithdrawalRequest> findByWalletId(@Param("walletId") Long walletId,
                                                 @Param("residenceId") Long residenceId,
                                                 Pageable pageable);
}
