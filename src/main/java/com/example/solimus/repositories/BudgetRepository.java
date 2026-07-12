package com.example.solimus.repositories;

import com.example.solimus.entities.Budget;
import com.example.solimus.enums.BudgetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface
BudgetRepository extends JpaRepository<Budget, Long> {

    /**
     * Trouver un budget par résidence et année.
     * Un seul budget actif par résidence/année.
     */
    Optional<Budget> findByResidenceIdAndAnnee(Long residenceId, Integer annee);

    /**
     * Trouver un budget actif par résidence et année.
     */
    Optional<Budget> findByResidenceIdAndAnneeAndStatus(Long residenceId, Integer annee, BudgetStatus status);

    List<Budget> findBySyndicIdAndStatus(Long syndicId, BudgetStatus status);

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

    // Pagine les budgets d'un syndic, triés selon le Pageable fourni (createdAt desc dans notre cas)
    Page<Budget> findBySyndicId(Long syndicId, Pageable pageable);

    // Compte le nombre total de budgets d'un syndic (toutes années, tous statuts)
    Integer countBySyndicId(Long syndicId);

    // Compte le nombre de budgets d'un syndic ayant un statut précis (ex: ACTIVE)
    Integer countBySyndicIdAndStatus(Long syndicId, BudgetStatus status);
}
