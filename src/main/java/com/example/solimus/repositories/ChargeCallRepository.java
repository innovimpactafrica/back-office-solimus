package com.example.solimus.repositories;

import com.example.solimus.entities.ChargeCall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChargeCallRepository extends JpaRepository<ChargeCall, Long> {

    /**
     * Trouver un appel de charges par budget, année et numéro de période.
     */
    Optional<ChargeCall> findByBudgetIdAndYearAndPeriodNumber(Long budgetId, Integer year, Integer periodNumber);



    /** Récupère les ChargeCall dont le budget appartient au syndic connecté*/
    Page<ChargeCall> findByBudgetSyndicId(Long syndicId, Pageable pageable);

    /**
     * Lister tous les appels de charges d'un budget.
     */
    List<ChargeCall> findByBudgetId(Long budgetId);

    // Même filtre, paginé — LIMIT/OFFSET géré par la base (onglet "Appels de charges liés" d'un budget)
    @Query("SELECT cc FROM ChargeCall cc WHERE cc.budget.id = :budgetId ORDER BY cc.periodNumber ASC")
    Page<ChargeCall> findByBudgetId(@Param("budgetId") Long budgetId, Pageable pageable);

    /**
     * Vérifie si des appels de charges existent pour un budget.
     */
    boolean existsByBudgetId(Long budgetId);

    /**
     * Lister tous les appels de charges d'une résidence.
     */
    List<ChargeCall> findByBudgetResidenceId(Long residenceId);

    /**
     * Trouver l'appel de charges le plus récent pour une résidence
     * (trié par année décroissante, puis numéro de période décroissant)
     */
    @Query("SELECT cc FROM ChargeCall cc " +
           "WHERE cc.budget.residence.id = :residenceId " +
           "ORDER BY cc.year DESC, cc.periodNumber DESC " +
           "LIMIT 1")
    Optional<ChargeCall> findMostRecentByResidenceId(@Param("residenceId") Long residenceId);

    /**
     * Lister tous les appels de charges d'une résidence pour une année, triés par période
     */
    @Query("SELECT cc FROM ChargeCall cc " +
           "WHERE cc.budget.residence.id = :residenceId " +
           "AND cc.year = :year " +
           "ORDER BY cc.periodNumber ASC")
    List<ChargeCall> findByResidenceIdAndYear(@Param("residenceId") Long residenceId, @Param("year") Integer year);

    List<ChargeCall> findByBudgetSyndicId(Long syndicId);
    List<ChargeCall> findByBudgetSyndicIdAndCreatedAtBetween(Long syndicId, LocalDateTime start, LocalDateTime end);

    /**
     * Page des appels de charges d'un syndic, filtres résidence ET année tous les deux optionnels —
     * pour /api/syndic/budget/charge-calls : aucun filtre par défaut (aligné sur wallet-transactions,
     * pas de pré-filtrage sur l'année en cours), residenceId/year affinent si fournis
     */
    @Query("SELECT cc FROM ChargeCall cc " +
           "WHERE cc.budget.syndic.id = :syndicId " +
           "AND (:year IS NULL OR cc.year = :year) " +
           "AND (:residenceId IS NULL OR cc.budget.residence.id = :residenceId) " +
           "ORDER BY cc.year DESC, cc.periodNumber ASC")
    Page<ChargeCall> findBySyndicIdAndYearAndOptionalResidence(
            @Param("syndicId") Long syndicId,
            @Param("year") Integer year,
            @Param("residenceId") Long residenceId,
            Pageable pageable);
}
