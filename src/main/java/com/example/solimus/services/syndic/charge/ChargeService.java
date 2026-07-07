package com.example.solimus.services.syndic.charge;

import com.example.solimus.dtos.charge.CreateExceptionalCallDTO;
import com.example.solimus.dtos.charge.ExceptionalCallDetailDTO;
import com.example.solimus.dtos.charge.UpdateExceptionalCallFinancialDTO;
import com.example.solimus.dtos.syndic.charge.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    //--------------------------------------------------
    // ===== APPEL DE CHARGES =====
    //--------------------------------------------------

    /**
     * Aperçu avant génération d'un appel de charges.
     * Retourne les données calculées sans rien créer en base.
     */
    ChargeCallPreviewDTO previewChargeCall(Long budgetId, Integer periodNumber);

    /**
     * Génère un appel de charges et envoie les emails aux copropriétaires.
     */
    void generateChargeCall(Long budgetId, GenerateChargeCallDTO dto);

    //--------------------------------------------------
    // ===== APPEL DE CHARGES EXCEPTIONNEL =====
    //--------------------------------------------------

    /**
     * Créer un Appel Exceptionnel — Section 1 (Informations générales)
     * Crée l'entité en statut BROUILLON, complétée par les sections suivantes
     */
    ExceptionalCallDetailDTO createExceptionalCall(CreateExceptionalCallDTO dto);

    /**
     * Mettre à jour les informations financières d'un appel exceptionnel
     */
    ExceptionalCallDetailDTO updateExceptionalCallFinancialInfo(Long exceptionalCallId, UpdateExceptionalCallFinancialDTO dto);

    /**
     * Activer un appel exceptionnel et envoyer les emails aux copropriétaires
     */
    ExceptionalCallDetailDTO activateExceptionalCall(Long exceptionalCallId, Boolean sendEmails, List<MultipartFile> documents);

    /**
     * Recherche d'équipements communs pour autocomplétion des postes budgétaires
     */
    List<CommonFacilitySuggestionDTO> searchCommonFacilities(Long residenceId, String q);

    /**
     * Retourne la liste paginée des budgets du syndic + totaux globaux (nb budgets, nb actifs)
     */
    BudgetListResponse getBudgetsForSyndic( int page, int size);

    /** Retourne le détail complet d'un budget pour la vue "carte KPI" : budget total, dépenses réelles
     globales, écart, consommation, et le tableau des postes (montantReel = montantPrevu en V1)*/
    BudgetOverviewDTO getBudgetOverview(Long budgetId);
}
