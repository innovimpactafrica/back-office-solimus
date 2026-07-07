package com.example.solimus.controllers;

import com.example.solimus.dtos.charge.*;
import com.example.solimus.dtos.syndic.charge.*;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.services.syndic.charge.ChargeService;
import com.example.solimus.services.syndic.residence.SyndicResidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "Lister les budgets", description = "Retourne la liste paginée des budgets du syndic connecté avec les totaux globaux (nombre de budgets, nombre de budgets actifs)")
    @GetMapping("/budgets")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetListResponse> getBudgets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        BudgetListResponse response = chargeService.getBudgetsForSyndic(page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Détail d'un budget avec KPIs", description = "Retourne le détail d'un budget avec les 4 KPIs (total, dépenses réelles, écart, consommation) et le tableau des postes budgétaires")
    @GetMapping("/budgets/{id}/overview")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetOverviewDTO> getBudgetOverview(@PathVariable Long id) {
        BudgetOverviewDTO overview = chargeService.getBudgetOverview(id);
        return ResponseEntity.ok(overview);
    }

    @Operation(summary = "Détail d'un budget", description = "Récupère le détail complet d'un budget avec la répartition par copropriétaire")
    @GetMapping("/{budgetId}")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetDetailDTO> getBudgetDetail(@PathVariable Long budgetId) {
        return ResponseEntity.ok(chargeService.getBudgetDetail(budgetId));
    }

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

    // ============================================================
    // APPEL DE CHARGES EXCEPTIONNEL
    // ============================================================

    @Operation(summary = "Créer un Appel Exceptionnel — Section 1 (Informations générales)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/exceptional-calls")
    public ResponseEntity<ExceptionalCallDetailDTO> createExceptionalCall(@RequestBody @Valid CreateExceptionalCallDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.createExceptionalCall(dto));
    }

    @Operation(summary = "Compléter un Appel Exceptionnel — Section 2 (Informations financières)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping("/exceptional-calls/{exceptionalCallId}/financial-info")
    public ResponseEntity<ExceptionalCallDetailDTO> updateFinancialInfo(
            @PathVariable Long exceptionalCallId,
            @RequestBody @Valid UpdateExceptionalCallFinancialDTO dto) {
        return ResponseEntity.ok(chargeService.updateExceptionalCallFinancialInfo(exceptionalCallId, dto));
    }

    @Operation(summary = "Activer un Appel Exceptionnel — Section 3 (Validation & Documents)", tags = {"Syndic - Charges Exceptionnelles"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping(value = "/exceptional-calls/{exceptionalCallId}/activate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExceptionalCallDetailDTO> activateExceptionalCall(
            @PathVariable Long exceptionalCallId,
            @RequestParam Boolean requiresAgValidation,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.ok(chargeService.activateExceptionalCall(exceptionalCallId, requiresAgValidation, files));
    }

    @Operation(summary = "Recherche d'équipements communs pour autocomplétion des postes budgétaires", tags = {"Syndic - Budget Prévisionnel"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/common-facilities/search")
    public ResponseEntity<List<CommonFacilitySuggestionDTO>> searchCommonFacilities(
            @PathVariable Long residenceId,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(chargeService.searchCommonFacilities(residenceId, q));
    }
}
