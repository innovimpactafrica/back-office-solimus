package com.example.solimus.repositories;

import com.example.solimus.entities.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour l'entité Budget.
 */
@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    /**
     * Trouver un budget par résidence et année.
     * Un seul budget actif par résidence/année.
     */
    Optional<Budget> findByResidenceIdAndAnnee(Long residenceId, Integer annee);
}
