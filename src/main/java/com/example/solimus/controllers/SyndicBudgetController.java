package com.example.solimus.controllers;


import com.example.solimus.dtos.syndic.charge.*;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.services.syndic.charge.ChargeService;
import com.example.solimus.services.syndic.residence.SyndicResidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Year;
import java.util.List;

@RestController
@RequestMapping("/api/syndic/budget")
@RequiredArgsConstructor
@Tag(name = "Syndic - Budget Prévisionnel", description = "Gestion du budget prévisionnel par le syndic")
public class SyndicBudgetController {

    private final ChargeService chargeService;
    private final SyndicResidenceService syndicResidenceService;

    @Operation(summary = "Lister les résidences du syndic", description = "Récupère toutes les résidences gérées par le syndic connecté")
    @GetMapping("/residences")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<List<ResidenceDTO>> getMesResidences() {
        return ResponseEntity.ok(syndicResidenceService.getMesResidences());
    }

    @Operation(summary = "Années disponibles pour budget", description = "Retourne l'année actuelle et les 3 prochaines années")
    @GetMapping("/years")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<List<Integer>> getAvailableYears() {
        int currentYear = Year.now().getValue();
        List<Integer> years = List.of(currentYear, currentYear + 1, currentYear + 2, currentYear + 3);
        return ResponseEntity.ok(years);
    }

    @Operation(summary = "Aperçu de la résidence pour création de budget", description = "Récupère les informations de la résidence avec la liste des copropriétaires et leurs tantièmes")
    @GetMapping("/residence/{residenceId}/preview")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetResidencePreviewDTO> getResidencePreview(@PathVariable Long residenceId) {
        return ResponseEntity.ok(chargeService.getResidencePreview(residenceId));
    }

    @Operation(summary = "Créer un budget prévisionnel", description = "Crée un nouveau budget prévisionnel pour une résidence avec ses postes budgétaires")
    @PostMapping
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetDetailDTO> createBudget(@RequestBody CreateBudgetDTO dto) {
        return ResponseEntity.ok(chargeService.createBudget(dto));
    }

    @Operation(summary = "Mettre à jour partiellement un budget prévisionnel", description = "Met à jour partiellement un budget existant. Seuls les champs fournis sont mis à jour.")
    @PatchMapping("/{budgetId}")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetDetailDTO> updateBudget(
            @PathVariable Long budgetId,
            @RequestBody UpdateBudgetDTO dto) {
        return ResponseEntity.ok(chargeService.updateBudget(budgetId, dto));
    }

    @Operation(summary = "Lister les budgets", description = "Retourne la liste paginée des budgets du syndic connecté avec les totaux globaux (nombre de budgets, nombre de budgets actifs)")
    @GetMapping("/budgets")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetListResponse> getBudgets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        BudgetListResponse response = chargeService.getBudgetsForSyndic(page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Détail d'un budget avec KPIs Onglet 1", description = "Retourne le détail d'un budget avec les 4 KPIs (total, dépenses réelles, écart, consommation) et le tableau des postes budgétaires")
    @GetMapping("/budgets/{id}/overview")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetOverviewDTO> getBudgetOverview(@PathVariable Long id) {
        BudgetOverviewDTO overview = chargeService.getBudgetOverview(id);
        return ResponseEntity.ok(overview);
    }

     @Operation(summary = "Répartition du budget entre copropriétaires(onglet 2 poste Budgetaire)", description = "Retourne la quote-part de chaque copropriétaire sur ce budget, calculée via son tantième")
    @GetMapping("/budgets/{id}/repartition")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<Page<BudgetRepartitionItemDTO>> getBudgetRepartition(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(chargeService.getBudgetRepartition(id, page, size));
    }

    @Operation(summary = "Liste des appels de charges liés à un budget", description = "Retourne tous les appels de charges générés pour ce budget avec leur statut calculé à la volée")
    @GetMapping("/budgets/{id}/charge-calls")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<Page<BudgetLinkedChargeCallDTO>> getBudgetLinkedChargeCalls(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(chargeService.getBudgetLinkedChargeCalls(id, page, size));
    }

    @Operation(summary = "Historique d'un budget (onglet 4 poste budgetaire)", description = "Retourne le journal des événements d'un budget (création, clôture...)")
    @GetMapping("/budgets/{id}/history")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<Page<HistoryItemDTO>> getBudgetHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(chargeService.getBudgetHistory(id, page, size));
    }

    @Operation(summary = "Détail d'un budget", description = "Récupère le détail complet d'un budget avec la répartition par copropriétaire")
    @GetMapping("/{budgetId}")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetDetailDTO> getBudgetDetail(@PathVariable Long budgetId) {
        return ResponseEntity.ok(chargeService.getBudgetDetail(budgetId));
    }

    // ============================================================
    // APPEL DE CHARGES
    // ============================================================
    @Operation(summary = "Aperçu avant génération d'appel de charges", description = "Retourne les données calculées sans rien créer en base")
    @GetMapping("/{budgetId}/charge-call-preview")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<ChargeCallPreviewDTO> previewChargeCall(
            @PathVariable Long budgetId,
            @RequestParam Integer periodNumber) {
        return ResponseEntity.ok(chargeService.previewChargeCall(budgetId, periodNumber));
    }

    @Operation(summary = "Générer un appel de charges", description = "Génère l'appel de charges et envoie les emails aux copropriétaires")
    @PostMapping("/{budgetId}/generate-charge-call")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<Void> generateChargeCall(
            @PathVariable Long budgetId,
            @RequestBody @Valid GenerateChargeCallDTO dto) {
        chargeService.generateChargeCall(budgetId, dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/charge-calls")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<ChargeCallListResponse> getChargeCalls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getChargeCallsForSyndic(page, size));
    }

    @GetMapping("/charge-calls/{id}")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<ChargeCallDetailDTO> getChargeCallDetail(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.getChargeCallDetail(id));
    }

    @PatchMapping("/charge-calls/{id}/remind")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<Integer> remindChargeCall(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.remindChargeCall(id));
    }

    // ============================================================
    // APPEL DE CHARGES EXCEPTIONNEL
    // ============================================================

    @Operation(summary = "Créer un Appel Exceptionnel — Section 1 (Informations générales)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/exceptional-calls")
    public ResponseEntity<ExceptionalCallDetailDTO> createExceptionalCall(@RequestBody @Valid CreateExceptionalCallDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.createExceptionalCall(dto));
    }

    @Operation(summary = "Compléter les informations financières d'un appel exceptionnel — Section 2", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping("/exceptional-calls/{id}/financial-info")
    public ResponseEntity<ExceptionalCallDetailDTO> updateExceptionalCallFinancialInfo(
            @PathVariable Long id,
            @RequestBody @Valid UpdateExceptionalCallFinancialDTO dto) {
        return ResponseEntity.ok(chargeService.updateExceptionalCallFinancialInfo(id, dto));
    }

    @Operation(summary = "Activer un appel exceptionnel — Section 3 (Validation & Documents)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping(value = "/exceptional-calls/{id}/activate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExceptionalCallDetailDTO> activateExceptionalCall(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean requiresAgValidation,
            @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {
        return ResponseEntity.ok(chargeService.activateExceptionalCall(id, requiresAgValidation, documents));
    }

    @Operation(summary = "Lister les appels exceptionnels du syndic", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/exceptional-calls")
    public ResponseEntity<ExceptionalCallListResponse> getExceptionalCalls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallsForSyndic(page, size));
    }

    @Operation(summary = "Vue d'ensemble d'un appel exceptionnel (onglet 1)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/exceptional-calls/{id}/overview")
    public ResponseEntity<ExceptionalCallOverviewDTO> getExceptionalCallOverview(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.getExceptionalCallOverview(id));
    }

    @Operation(summary = "Répartition d'un appel exceptionnel entre copropriétaires (onglet 2)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/exceptional-calls/{id}/repartition")
    public ResponseEntity<Page<ExceptionalCallItemDetailDTO>> getExceptionalCallRepartition(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallRepartition(id, page, size));
    }

    @Operation(summary = "Paiements reçus pour un appel exceptionnel (onglet 3)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/exceptional-calls/{id}/payments")
    public ResponseEntity<Page<ExceptionalCallPaymentDTO>> getExceptionalCallPayments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallPayments(id, page, size));
    }

    @Operation(summary = "Documents rattachés à un appel exceptionnel (onglet 4)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/exceptional-calls/{id}/documents")
    public ResponseEntity<Page<ExceptionalCallDocumentDTO>> getExceptionalCallDocuments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallDocuments(id, page, size));
    }

    @Operation(summary = "Historique des événements d'un appel exceptionnel (onglet 5)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/exceptional-calls/{id}/history")
    public ResponseEntity<Page<ExceptionalCallHistoryDTO>> getExceptionalCallHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallHistory(id, page, size));
    }

    @Operation(summary = "Clôturer un appel exceptionnel", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping("/exceptional-calls/{id}/close")
    public ResponseEntity<Void> closeExceptionalCall(@PathVariable Long id) {
        chargeService.closeExceptionalCall(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Recherche d'équipements communs pour autocomplétion des postes budgétaires", tags = {"Syndic - Budget Prévisionnel"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/common-facilities/search")
    public ResponseEntity<Page<CommonFacilitySuggestionDTO>> searchCommonFacilities(
            @PathVariable Long residenceId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(chargeService.searchCommonFacilities(residenceId, q, page, size));
    }
    // =========================================================================
    // PAIEMENTS / IMPAYÉS (global syndic)
    // =========================================================================

    @Operation(summary = "Liste des paiements du syndic (toutes résidences)", tags = {"Syndic - Charges"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/payments")
    public ResponseEntity<PaymentListResponse> getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(chargeService.getPaymentsForSyndic(page, size, search));
    }

    @Operation(summary = "Liste des impayés du syndic (toutes résidences)", tags = {"Syndic - Charges"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/unpaid")
    public ResponseEntity<UnpaidListResponse> getUnpaid(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getUnpaidForSyndic(page, size));
    }

    @Operation(summary = "Relancer un copropriétaire pour une charge impayée précise", tags = {"Syndic - Charges"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping("/unpaid/{chargeCallItemId}/remind")
    public ResponseEntity<Void> remindUnpaidItem(@PathVariable Long chargeCallItemId) {
        chargeService.remindUnpaidItem(chargeCallItemId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Relancer tous les copropriétaires en impayé", tags = {"Syndic - Charges"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping("/unpaid/remind-all")
    public ResponseEntity<Integer> remindAllUnpaid() {
        return ResponseEntity.ok(chargeService.remindAllUnpaid());
    }

    //=========================================================================
    // DASHBOARD "GESTION DES CHARGES"
    // =========================================================================

    @Operation(summary = "Dashboard 'Gestion des charges'", description = "KPIs globaux + graphiques (encaissement mensuel, répartition des postes)", tags = {"Syndic - Charges"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/dashboard")
    public ResponseEntity<ChargeDashboardDTO> getChargeDashboard(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(chargeService.getChargeDashboard(residenceId));
    }
}
