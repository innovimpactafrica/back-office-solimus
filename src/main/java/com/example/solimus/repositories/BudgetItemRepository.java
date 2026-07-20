package com.example.solimus.repositories;

import com.example.solimus.entities.BudgetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    // Récupère les postes budgétaires sans bien commun pour une résidence et une année
    @Query("SELECT bi FROM BudgetItem bi WHERE bi.budget.residence.id = :residenceId AND bi.budget.annee = :year AND bi.commonFacility IS NULL")
    List<BudgetItem> findByResidenceIdAndYearAndCommonFacilityIsNull(@Param("residenceId") Long residenceId, @Param("year") Integer year);
}
