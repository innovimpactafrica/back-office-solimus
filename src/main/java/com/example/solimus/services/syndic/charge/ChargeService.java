package com.example.solimus.services.syndic.charge;

import com.example.solimus.dtos.syndic.charge.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;

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
     * Recherche d'équipements communs pour autocomplétion des postes budgétaires
     */
    Page<CommonFacilitySuggestionDTO> searchCommonFacilities(Long residenceId, String q, Integer page, Integer size);

    /**
     * Retourne la liste paginée des budgets du syndic + totaux globaux (nb budgets, nb actifs)
     */
    BudgetListResponse getBudgetsForSyndic(int page, int size);

    /**
     * Consulte le détail d'un budget existant — même structure
     * que createBudget, réutilisée pour l'affichage après coup.
     */
    BudgetDetailDTO getBudgetDetail(Long budgetId);

    /**
     * Met à jour partiellement un budget prévisionnel existant.
     * Seuls les champs fournis sont mis à jour.
     */
    BudgetDetailDTO updateBudget(Long budgetId, UpdateBudgetDTO dto);


    /** Retourne le détail complet d'un budget pour la vue "carte KPI" : budget total, dépenses réelles
     globales, écart, consommation, et le tableau des postes (montantReel = montantPrevu en V1)*/
    BudgetOverviewDTO getBudgetOverview(Long budgetId);

    /**
     * Répartition du budget entre copropriétaires (onglet 2)
     * Retourne la quote-part de chaque copropriétaire sur ce budget, calculée via son tantième
     */
    Page<BudgetRepartitionItemDTO> getBudgetRepartition(Long budgetId, Integer page, Integer size);

    /**
     * Liste des appels de charges liés à un budget (onglet 3)
     * Retourne tous les appels de charges générés pour ce budget avec leur statut calculé à la volée
     */
    Page<BudgetLinkedChargeCallDTO> getBudgetLinkedChargeCalls(Long budgetId, Integer page, Integer size);

    /**
     * Retourne l'historique des événements d'un budget (création, clôture... onglet 4)
     */
    Page<HistoryItemDTO> getBudgetHistory(Long budgetId, Integer page, Integer size);

    /**
     * Clôturer un budget
     * Change le statut du budget à CLOSED et trace l'action dans le journal d'activité
     */
    void closeBudget(Long budgetId);

  
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

    
    /**
     * Lister les appels de charges du syndic
     * Retourne la liste paginée des appels de charges du syndic connecté avec les totaux globaux
     */
    ChargeCallListResponse getChargeCallsForSyndic(int page, int size);

    /**
     * Détail d'un appel de charges
     * Retourne le détail complet d'un appel de charges avec les KPIs et le suivi par copropriétaire
     */
    ChargeCallDetailDTO getChargeCallDetail(Long chargeCallId);

    /**
     * Rappeler un appel de charges
     * Renvoie un email de rappel aux copropriétaires qui n'ont pas encore payé
     * Retourne le nombre de rappels envoyés
     */
    int remindChargeCall(Long chargeCallId);

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
     * Lister les appels exceptionnels du syndic
     * Retourne la liste paginée des appels exceptionnels du syndic connecté
     */
    ExceptionalCallListResponse getExceptionalCallsForSyndic(int page, int size);

    /**
     * Vue d'ensemble d'un appel exceptionnel (onglet 1)
     * Retourne les KPIs et informations générales
     */
    ExceptionalCallOverviewDTO getExceptionalCallOverview(Long exceptionalCallId);

    /**
     * Répartition d'un appel exceptionnel entre copropriétaires (onglet 2)
     */
    Page<ExceptionalCallItemDetailDTO> getExceptionalCallRepartition(Long exceptionalCallId, int page, int size);

    /**
     * Paiements reçus pour un appel exceptionnel (onglet 3)
     */
    Page<ExceptionalCallPaymentDTO> getExceptionalCallPayments(Long exceptionalCallId, int page, int size);

    /**
     * Documents rattachés à un appel exceptionnel (onglet 4)
     */
    Page<ExceptionalCallDocumentDTO> getExceptionalCallDocuments(Long exceptionalCallId, int page, int size);

    /**
     * Historique des événements d'un appel exceptionnel (onglet 5)
     */
    Page<ExceptionalCallHistoryDTO> getExceptionalCallHistory(Long exceptionalCallId, int page, int size);

    /**
     * Clôturer un appel exceptionnel
     * Change le statut à TERMINE et trace l'action dans le journal d'activité
     */
    void closeExceptionalCall(Long exceptionalCallId);

    //--------------------------------------------------
    // ===== PAIEMENTS / IMPAYÉS (GLOBAL SYNDIC) =====
    //--------------------------------------------------

    /**
     * Liste paginée des paiements du syndic, avec recherche optionnelle par nom de copropriétaire
     */
    PaymentListResponse getPaymentsForSyndic(int page, int size, String search);

    /**
     * Liste paginée des impayés du syndic (charges non soldées)
     */
    UnpaidListResponse getUnpaidForSyndic(int page, int size);

    /**
     * Relance un copropriétaire pour une charge impayée précise
     */
    void remindUnpaidItem(Long chargeCallItemId);

    /**
     * Relance tous les copropriétaires ayant une charge impayée
     * Retourne le nombre de relances envoyées
     */
    int remindAllUnpaid();
    /**
     * Dashboard "Gestion des charges" — KPIs globaux + graphiques
     * residenceId optionnel : sert uniquement pour le camembert (répartition par poste),
     * défaut sur la première résidence active du syndic si non fourni
     */
    ChargeDashboardDTO getChargeDashboard(Long residenceId);



}
