package com.example.solimus.repositories;

import com.example.solimus.entities.BudgetCoOwnerAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité BudgetCoOwnerAllocation (quote-part annuelle figée par budget).
 */
@Repository
public interface BudgetCoOwnerAllocationRepository extends JpaRepository<BudgetCoOwnerAllocation, Long> {

    // Quote-part figée d'un copropriétaire précis, pour un budget précis
    Optional<BudgetCoOwnerAllocation> findByBudgetIdAndCoOwnerId(Long budgetId, Long coOwnerId);

    // Toutes les quote-parts figées d'un budget (répartition annuelle complète)
    List<BudgetCoOwnerAllocation> findByBudgetId(Long budgetId);

    // Supprime toutes les allocations d'un budget — avant un recalcul complet, ou lors de la
    // suppression du budget lui-même
    void deleteByBudgetId(Long budgetId);
}
