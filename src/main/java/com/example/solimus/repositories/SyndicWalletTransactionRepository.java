package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicWalletTransaction;
import com.example.solimus.enums.WalletTransactionCategory;
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
     * Somme toutes les transactions d'une résidence jusqu'à une date donnée
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM SyndicWalletTransaction t " +
           "WHERE t.residence.id = :residenceId AND t.transactionDate <= :asOfDate")
    BigDecimal sumAllByResidenceId(@Param("residenceId") Long residenceId, @Param("asOfDate") LocalDateTime asOfDate);

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

    /**
     *  Somme des transactions TRAVAUX d'un équipement commun précis, sur une période donnée.
     *  Chaque CommonFacility appartient à une seule résidence (jamais partagé entre résidences,
     *  même si son type comme "Ascenseur" est générique). Mais un même équipement reste identique
     *  d'une année à l'autre, donc le filtre sur start/end (année du budget) reste indispensable.
     *
      */
    @Query("SELECT COALESCE(SUM(ABS(t.amount)), 0) FROM SyndicWalletTransaction t " +
            "WHERE t.interventionRequest.commonFacility.id = :facilityId " +
            "AND t.category = 'TRAVAUX' " +
            "AND t.transactionDate >= :start AND t.transactionDate < :end")
    BigDecimal sumByCommonFacilityAndPeriod(
            @Param("facilityId") Long facilityId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Somme les transactions d'une catégorie précise (ex: TRAVAUX), pour un wallet et une période donnés
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM SyndicWalletTransaction t " +
           "WHERE t.wallet.id = :walletId AND t.category = :category " +
           "AND t.transactionDate >= :start AND t.transactionDate < :end")
    BigDecimal sumByCategoryAndPeriod(@Param("walletId") Long walletId,
            @Param("category") WalletTransactionCategory category,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Récupère les transactions les plus récentes d'un wallet, filtrées par catégorie
    List<SyndicWalletTransaction> findTopByWalletIdAndCategoryOrderByTransactionDateDesc(
            Long walletId, WalletTransactionCategory category, Pageable pageable);

    // Additionne les montants d'une catégorie sur une période donnee, optionnellement filtré par résidence
   // Utilisée pour le KPI "Charges Collectées" (catégorie CHARGES, période = trimestre en cours)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM SyndicWalletTransaction t " +
            "WHERE t.wallet.id = :walletId AND t.category = :category " +
            "AND t.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:residenceId IS NULL OR t.residence.id = :residenceId)")
    BigDecimal sumAmountByCategoryAndPeriod(@Param("walletId") Long walletId,
                                            @Param("category") WalletTransactionCategory category,
                                            @Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate,
                                            @Param("residenceId") Long residenceId);

    // Additionne les montants d'une catégorie, depuis toujours (aucune limite de période), optionnellement filtré par résidence
    // Utilisée pour le KPI "Paiement prestataires" (catégorie TRAVAUX, pas de limite de date)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM SyndicWalletTransaction t " +
            "WHERE t.wallet.id = :walletId AND t.category = :category " +
            "AND (:residenceId IS NULL OR t.residence.id = :residenceId)")
    BigDecimal sumAmountByCategory(@Param("walletId") Long walletId,
                                   @Param("category") WalletTransactionCategory category,
                                   @Param("residenceId") Long residenceId);


    // Compte le nombre de transactions d'une catégorie, depuis toujours, optionnellement filtre par residence
    // Utilisée pour le sous-texte "X facture" du KPI "Paiement prestataires" (catégorie TRAVAUX)
    @Query("SELECT COUNT(t) FROM SyndicWalletTransaction t " +
            "WHERE t.wallet.id = :walletId AND t.category = :category " +
            "AND (:residenceId IS NULL OR t.residence.id = :residenceId)")
    long countByCategory(@Param("walletId") Long walletId,
                         @Param("category") WalletTransactionCategory category,
                         @Param("residenceId") Long residenceId);


    // Somme mensuelle d'une catégorie, sur une période donnée, optionnellement filtré par résidence
   // Utilisée pour construire le graphique "Recettes vs Dépenses" (6 derniers mois glissants)
    @Query(value =
            "SELECT DATE_FORMAT(t.transaction_date, '%Y-%m') AS period_key, " +
                    "       COALESCE(SUM(t.amount), 0) AS total " +
                    "FROM syndic_wallet_transactions t " +
                    "WHERE t.wallet_id = :walletId " +
                    "AND t.category = :category " +
                    "AND t.transaction_date >= :startDate " +
                    "AND (:residenceId IS NULL OR t.residence_id = :residenceId) " +
                    "GROUP BY period_key " +
                    "ORDER BY period_key ASC",
            nativeQuery = true)
    List<Object[]> sumMonthlyByCategory(@Param("walletId") Long walletId,
                                        @Param("category") String category,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("residenceId") Long residenceId);

    // Somme trimestrielle d'une catégorie, sur une période donnée, optionnellement filtré par résidence
    // Utilisée pour la vue trimestrielle du graphique "Recettes vs Dépenses" (4 trimestres de l'année en cours)
    @Query(value =
            "SELECT CONCAT(YEAR(t.transaction_date), '-Q', QUARTER(t.transaction_date)) AS period_key, " +
            "       COALESCE(SUM(t.amount), 0) AS total " +
            "FROM syndic_wallet_transactions t " +
            "WHERE t.wallet_id = :walletId " +
            "AND t.category = :category " +
            "AND t.transaction_date >= :startDate " +
            "AND (:residenceId IS NULL OR t.residence_id = :residenceId) " +
            "GROUP BY period_key " +
            "ORDER BY period_key ASC",
            nativeQuery = true)
    List<Object[]> sumQuarterlyByCategory(@Param("walletId") Long walletId,
                                        @Param("category") String category,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("residenceId") Long residenceId);


    // Transactions CHARGES et TRAVAUX uniquement (exclut RETRAIT), triées par date décroissante,
    // optionnellement filtré par residence. Utilisée pour le tableau "Derniers flux"
    @Query("SELECT t FROM SyndicWalletTransaction t " +
            "WHERE t.wallet.id = :walletId " +
            "AND t.category IN ('CHARGES', 'TRAVAUX') " +
            "AND (:residenceId IS NULL OR t.residence.id = :residenceId) " +
            "ORDER BY t.transactionDate DESC")
    Page<SyndicWalletTransaction> findFlowsByWallet(@Param("walletId") Long walletId,
                                                    @Param("residenceId") Long residenceId,
                                                    Pageable pageable);

}
