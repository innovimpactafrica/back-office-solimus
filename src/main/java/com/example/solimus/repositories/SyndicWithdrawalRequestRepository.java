package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicWithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

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
}
