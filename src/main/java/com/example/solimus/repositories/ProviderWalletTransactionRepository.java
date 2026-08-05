package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderWalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface ProviderWalletTransactionRepository extends JpaRepository<ProviderWalletTransaction, Long> {

    // Somme de toutes les transactions d'un wallet jusqu'à une date donnée 
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ProviderWalletTransaction t " +
           "WHERE t.wallet.id = :walletId AND t.transactionDate <= :asOfDate")
    BigDecimal sumTransactionsUpTo(@Param("walletId") Long walletId, @Param("asOfDate") LocalDateTime asOfDate);

    // Somme des transactions d'un wallet sur une période donnée — "Total reçu ce mois"
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ProviderWalletTransaction t " +
           "WHERE t.wallet.id = :walletId AND t.transactionDate BETWEEN :start AND :end")
    BigDecimal sumInPeriod(@Param("walletId") Long walletId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}