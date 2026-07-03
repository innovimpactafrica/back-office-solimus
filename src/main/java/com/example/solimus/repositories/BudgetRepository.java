package com.example.solimus.repositories;

import com.example.solimus.entities.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /**
     * Lister tous les budgets d'une résidence.
     */
    List<Budget> findByResidenceId(Long residenceId);

    /**
     * Trouver le budget le plus récent pour une résidence
     * (trié par année décroissante)
     */
    @Query("SELECT b FROM Budget b WHERE b.residence.id = :residenceId ORDER BY b.annee DESC")
    Optional<Budget> findMostRecentByResidenceId(@Param("residenceId") Long residenceId);
}
