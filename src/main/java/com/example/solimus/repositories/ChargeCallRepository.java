package com.example.solimus.repositories;

import com.example.solimus.entities.ChargeCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargeCallRepository extends JpaRepository<ChargeCall, Long> {

    /**
     * Trouver un appel de charges par budget, année et numéro de période.
     */
    Optional<ChargeCall> findByBudgetIdAndYearAndPeriodNumber(Long budgetId, Integer year, Integer periodNumber);

    /**
     * Lister tous les appels de charges d'un budget.
     */
    List<ChargeCall> findByBudgetId(Long budgetId);

    /**
     * Lister tous les appels de charges d'une résidence.
     */
    List<ChargeCall> findByBudgetResidenceId(Long residenceId);
}
