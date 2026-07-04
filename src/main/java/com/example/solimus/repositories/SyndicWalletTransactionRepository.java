package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicWalletTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SyndicWalletTransactionRepository extends JpaRepository<SyndicWalletTransaction, Long> {



    /**
     * Récupérer les N transactions les plus récentes pour une résidence
     * Triées par date décroissante
     */
    @Query("SELECT tw FROM SyndicWalletTransaction tw " +
           "WHERE tw.residence.id = :residenceId " +
           "ORDER BY tw.transactionDate DESC")
    List<SyndicWalletTransaction> findRecentByResidenceIdWithLimit(
            @Param("residenceId") Long residenceId,
            Pageable pageable);

     /**
     * Somme des transactions d'un wallet jusqu'à une date donnée
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM SyndicWalletTransaction t " +
           "WHERE t.wallet.id = :walletId AND t.transactionDate <= :asOfDate")
    BigDecimal sumTransactionsUpTo(@Param("walletId") Long walletId, @Param("asOfDate") LocalDateTime asOfDate);

    /**
     * Récupérer les transactions de catégorie TRAVAUX pour une résidence et une année donnée
     * Utilisé pour calculer la répartition des vraies dépenses (graphique camembert)
     */
    @Query("SELECT t FROM SyndicWalletTransaction t " +
           "WHERE t.residence.id = :residenceId " +
           "AND t.category = 'TRAVAUX' " +
           "AND YEAR(t.transactionDate) = :year")
    List<SyndicWalletTransaction> findTravauxByResidenceAndYear(
            @Param("residenceId") Long residenceId,
            @Param("year") int year);
}
