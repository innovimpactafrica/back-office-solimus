package com.example.solimus.services.syndic.charge;

import com.example.solimus.dtos.syndic.charge.BudgetDetailDTO;
import com.example.solimus.dtos.syndic.charge.BudgetResidencePreviewDTO;
import com.example.solimus.dtos.syndic.charge.CreateBudgetDTO;

public interface ChargeService {

    //--------------------------------------------------
    // ===== BUDGET PRÉVISIONNEL =====
    //--------------------------------------------------

    /**
     * Étape 1— aperçu d'une résidence avant création du budget.
     * Retourne la liste des copropriétaires avec leurs tantièmes,
     * pour validation visuelle avant de passer à l'étape 2.
     */
    BudgetResidencePreviewDTO getResidencePreview(Long residenceId);

    /**
     * Crée un budget prévisionnel (résidence + année + postes budgétaires)
     * et retourne le détail complet avec la répartition calculée
     * par copropriétaire. Un seul budget actif par résidence/année.
     */
    BudgetDetailDTO createBudget(CreateBudgetDTO dto);

    /**
     * Consulte le détail d'un budget existant — même structure
     * que createBudget, réutilisée pour l'affichage après coup.
     */
    BudgetDetailDTO getBudgetDetail(Long budgetId);
}
