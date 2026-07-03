package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicWalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyndicWalletTransactionRepository extends JpaRepository<SyndicWalletTransaction, Long> {

    /**
     * Récupérer les transactions récentes pour une résidence
     * Triées par date décroissante
     */
    @Query("SELECT tw FROM SyndicWalletTransaction tw " +
           "WHERE tw.residence.id = :residenceId " +
           "ORDER BY tw.transactionDate DESC")
    List<SyndicWalletTransaction> findRecentByResidenceId(@Param("residenceId") Long residenceId);

    /**
     * Récupérer les N transactions les plus récentes pour une résidence
     * Triées par date décroissante
     */
    @Query("SELECT tw FROM SyndicWalletTransaction tw " +
           "WHERE tw.residence.id = :residenceId " +
           "ORDER BY tw.transactionDate DESC")
    List<SyndicWalletTransaction> findRecentByResidenceIdWithLimit(
            @Param("residenceId") Long residenceId,
            org.springframework.data.domain.Pageable pageable);
}
