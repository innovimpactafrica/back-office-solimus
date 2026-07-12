package com.example.solimus.repositories;

import com.example.solimus.entities.ChargeCall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
           "ORDER BY cc.year DESC, cc.periodNumber DESC")
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
}
