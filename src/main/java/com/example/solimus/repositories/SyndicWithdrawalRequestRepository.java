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
}
