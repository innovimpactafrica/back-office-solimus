package com.example.solimus.repositories;

import com.example.solimus.entities.BudgetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour l'entité BudgetItem.
 */
@Repository
public interface BudgetItemRepository extends JpaRepository<BudgetItem, Long> {

    // Récupère le poste budgétaire le plus récent lié à cet équipement commun
   // (le plus récent = celui du budget avec l'année la plus élevée)
    Optional<BudgetItem> findFirstByCommonFacilityIdOrderByBudgetAnneeDesc(Long commonFacilityId);

    // Supprime tous les postes budgétaires d'un budget donné
    void deleteByBudgetId(Long budgetId);
}
