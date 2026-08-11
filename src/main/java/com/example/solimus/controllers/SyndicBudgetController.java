package com.example.solimus.controllers;


import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.charge.*;
import com.example.solimus.dtos.syndic.residence.ResidenceCardDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.enums.RepartitionMode;
import com.example.solimus.services.syndic.charge.ChargeService;
import com.example.solimus.services.syndic.residence.SyndicResidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

@RestController
@RequestMapping("/api/syndic/budget")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SYNDIC') and @planFeatureGuard.hasFeature('CHARGE_MANAGEMENT')")
@Tag(name = "Syndic - Charges", description = "Gestion des charges par le syndic")
public class SyndicBudgetController {

    private final ChargeService chargeService;
    private final SyndicResidenceService syndicResidenceService;

    @Operation(summary = "Lister les résidences du syndic", description = "Récupère toutes les résidences gérées par le syndic connecté", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ResidenceCardDTO.class)))
    })
    @GetMapping("/residences")
    public ResponseEntity<Page<ResidenceCardDTO>> getMesResidences(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicResidenceService.getResidencesPaginated(null, null, null, page, size));
    }

    @Operation(summary = "Lister les résidences avec budget actif", description = "Récupère les résidences du syndic connecté qui ont un budget actif", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ResidenceBudgetSummaryDTO.class)))
    })
    @GetMapping("/residences/with-active-budget")
    public ResponseEntity<Page<ResidenceBudgetSummaryDTO>> getResidencesWithActiveBudget(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getResidencesWithActiveBudget(page, size));
    }

    @Operation(summary = "Années disponibles pour budget", description = "Retourne l'année actuelle et les 3 prochaines années", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès")
    })
    @GetMapping("/years")
    public ResponseEntity<List<Integer>> getAvailableYears() {
        int currentYear = Year.now().getValue();
        List<Integer> years = List.of(currentYear, currentYear + 1, currentYear + 2, currentYear + 3);
        return ResponseEntity.ok(years);
    }

    @Operation(summary = "Aperçu de la résidence pour création de budget", description = "Récupère les informations de la résidence avec la liste des copropriétaires et leurs tantièmes", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aperçu renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = BudgetResidencePreviewDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/residence/{residenceId}/preview")
    public ResponseEntity<BudgetResidencePreviewDTO> getResidencePreview(@PathVariable Long residenceId) {
        return ResponseEntity.ok(chargeService.getResidencePreview(residenceId));
    }

    @Operation(summary = "Aperçu de la répartition d'un budget (avant création)",
            description = "Écran \"Nouveau Budget Prévisionnel\" (Étape 2 — Postes budgétaires). À appeler à chaque modification "
                    + "des postes saisis (léger débounce côté Front recommandé, ex: 300ms) — aucune donnée n'est enregistrée, "
                    + "le calcul utilise exactement la même méthode (plus grand reste) que la création réelle du budget, pour "
                    + "que l'aperçu corresponde toujours exactement au résultat final. Ne calculez plus rien côté Front.",
            tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aperçu renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = BudgetRepartitionPreviewDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/residence/{residenceId}/repartition-preview")
    public ResponseEntity<BudgetRepartitionPreviewDTO> previewBudgetRepartition(
            @PathVariable Long residenceId,
            @RequestBody BudgetRepartitionPreviewRequestDTO dto) {
        return ResponseEntity.ok(chargeService.previewBudgetRepartition(residenceId, dto));
    }

    @Operation(summary = "Aperçu appel de charges par résidence", description = "Retourne l'aperçu de l'appel de charges (budget id, année, résidence, répartition) en utilisant le budget actif de la résidence", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aperçu renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = ChargeCallPreviewDTO.class))),
            @ApiResponse(responseCode = "403", description = "Ce budget ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucun budget actif pour cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/residence/{residenceId}/charge-call-preview")
    public ResponseEntity<ChargeCallPreviewDTO> previewChargeCallByResidence(
            @PathVariable Long residenceId,
            @RequestParam Integer periodNumber) {
        return ResponseEntity.ok(chargeService.previewChargeCallByResidence(residenceId, periodNumber));
    }

    @Operation(summary = "Créer un budget prévisionnel", description = "Crée un nouveau budget prévisionnel pour une résidence avec ses postes budgétaires", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Budget créé avec succès",
                    content = @Content(schema = @Schema(implementation = BudgetDetailDTO.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides (ex: budget déjà existant pour cette résidence et cette année, libellé manquant pour un poste sans équipement commun, équipement commun n'appartenant pas à cette résidence)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à créer un budget pour cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence ou équipement commun introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping
    public ResponseEntity<BudgetDetailDTO> createBudget(@RequestBody CreateBudgetDTO dto) {
        return ResponseEntity.ok(chargeService.createBudget(dto));
    }


    @Operation(summary = "Lister les budgets", description = "Retourne la liste paginée des budgets du syndic connecté avec les totaux globaux (nombre de budgets, nombre de budgets actifs)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = BudgetListResponse.class)))
    })
    @GetMapping("/budgets")
    public ResponseEntity<BudgetListResponse> getBudgets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        BudgetListResponse response = chargeService.getBudgetsForSyndic(page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Détail d'un budget avec KPIs Onglet 1", description = "Retourne le détail d'un budget avec les 4 KPIs (total, dépenses réelles, écart, consommation) et le tableau des postes budgétaires", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = BudgetOverviewDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à ce budget",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Budget introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/budgets/{id}/overview")
    public ResponseEntity<BudgetOverviewDTO> getBudgetOverview(@PathVariable Long id) {
        BudgetOverviewDTO overview = chargeService.getBudgetOverview(id);
        return ResponseEntity.ok(overview);
    }

     @Operation(summary = "Répartition du budget entre copropriétaires(onglet 2 poste Budgetaire)", description = "Retourne la quote-part de chaque copropriétaire sur ce budget, calculée via son tantième", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Répartition renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = BudgetRepartitionItemDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à ce budget",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Budget introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/budgets/{id}/repartition")
    public ResponseEntity<Page<BudgetRepartitionItemDTO>> getBudgetRepartition(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(chargeService.getBudgetRepartition(id, page, size));
    }

    @Operation(summary = "Liste des appels de charges liés à un budget", description = "Retourne tous les appels de charges générés pour ce budget avec leur statut calculé à la volée", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = BudgetLinkedChargeCallDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à ce budget",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Budget introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/budgets/{id}/charge-calls")
    public ResponseEntity<Page<BudgetLinkedChargeCallDTO>> getBudgetLinkedChargeCalls(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(chargeService.getBudgetLinkedChargeCalls(id, page, size));
    }

    @Operation(summary = "Historique d'un budget (onglet 4 poste budgetaire)", description = "Retourne le journal des événements d'un budget (création, clôture...)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = HistoryItemDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à ce budget",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Budget introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/budgets/{id}/history")
    public ResponseEntity<Page<HistoryItemDTO>> getBudgetHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(chargeService.getBudgetHistory(id, page, size));
    }

    @Operation(summary = "Détail d'un budget", description = "Récupère le détail complet d'un budget avec la répartition par copropriétaire", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = BudgetDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à ce budget",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Budget introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{budgetId}")
    public ResponseEntity<BudgetDetailDTO> getBudgetDetail(@PathVariable Long budgetId) {
        return ResponseEntity.ok(chargeService.getBudgetDetail(budgetId));
    }

    @Operation(summary = "Clôturer un budget prévisionnel", description = "Change le statut du budget à CLOSED et trace l'action dans le journal d'activité", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Budget clôturé avec succès"),
            @ApiResponse(responseCode = "400", description = "Ce budget est déjà clôturé",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à clôturer ce budget",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Budget introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/{budgetId}/close")
    public ResponseEntity<Void> closeBudget(@PathVariable Long budgetId) {
        chargeService.closeBudget(budgetId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Réactiver un budget clôturé", description = "Change le statut du budget de CLOSED à ACTIVE et trace l'action dans le journal d'activité", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Budget réactivé avec succès"),
            @ApiResponse(responseCode = "400", description = "Seul un budget clôturé peut être réactivé",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à réactiver ce budget",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Budget introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/{budgetId}/reopen")
    public ResponseEntity<Void> reopenBudget(@PathVariable Long budgetId) {
        chargeService.reopenBudget(budgetId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer un budget prévisionnel", description = "Supprime le budget et tous ses postes budgétaires associés, même s'il est clôturé, tant qu'aucun appel de charges n'a été généré.", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Budget supprimé avec succès"),
            @ApiResponse(responseCode = "400", description = "Impossible de supprimer : des appels de charges ont déjà été générés pour ce budget",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à supprimer ce budget",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Budget introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> deleteBudget(@PathVariable Long budgetId) {
        chargeService.deleteBudget(budgetId);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // APPEL DE CHARGES
    // ============================================================
    @Operation(summary = "Générer un appel de charges", description = "Génère l'appel de charges et envoie les emails aux copropriétaires", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Appel de charges généré avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides (ex: appel déjà existant pour cette période, numéro de période hors bornes, montants personnalisés manquants ou n'égalant pas le total)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Ce budget ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucun budget actif pour cette résidence, ou copropriétaire introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/residence/{residenceId}/generate-charge-call")
    public ResponseEntity<Void> generateChargeCall(
            @PathVariable Long residenceId,
            @RequestBody @Valid GenerateChargeCallDTO dto) {
        chargeService.generateChargeCall(residenceId, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lister les appels de charges", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ChargeCallListResponse.class)))
    })
    @GetMapping("/charge-calls")
    public ResponseEntity<ChargeCallListResponse> getChargeCalls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getChargeCallsForSyndic(page, size));
    }

    @Operation(summary = "Détail d'un appel de charges", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = ChargeCallDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cet appel de charges",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel de charges introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/charge-calls/{id}")
    public ResponseEntity<ChargeCallDetailDTO> getChargeCallDetail(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.getChargeCallDetail(id));
    }

    @Operation(summary = "Relancer un appel de charges", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relance envoyée avec succès (nombre de copropriétaires relancés)"),
            @ApiResponse(responseCode = "400", description = "Impossible de relancer : cet appel de charges est déjà soldé",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à relancer cet appel de charges",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel de charges introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/charge-calls/{id}/remind")
    public ResponseEntity<Integer> remindChargeCall(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.remindChargeCall(id));
    }

    @Operation(summary = "Supprimer un appel de charges", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Appel de charges supprimé avec succès"),
            @ApiResponse(responseCode = "400", description = "Impossible de supprimer : des paiements ont déjà été effectués sur cet appel de charges",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à supprimer cet appel de charges",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel de charges introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/charge-calls/{id}")
    public ResponseEntity<Void> deleteChargeCall(@PathVariable Long id) {
        chargeService.deleteChargeCall(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer un appel exceptionnel", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Appel exceptionnel supprimé avec succès"),
            @ApiResponse(responseCode = "400", description = "Impossible de supprimer : des paiements ont déjà été effectués sur cet appel exceptionnel",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cet appel exceptionnel ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel exceptionnel introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/exceptional-calls/{id}")
    public ResponseEntity<Void> deleteExceptionalCall(@PathVariable Long id) {
        chargeService.deleteExceptionalCall(id);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // APPEL DE CHARGES EXCEPTIONNEL
    // ============================================================

    @Operation(summary = "Aperçu de la répartition d'un appel exceptionnel (avant création)",
            description = "Écran \"Nouvel Appel Exceptionnel\". À appeler à chaque modification du montant total saisi "
                    + "(léger débounce côté Front recommandé, ex: 300ms) — aucune donnée n'est enregistrée, le calcul utilise "
                    + "exactement la même méthode (plus grand reste) que la création réelle, pour que l'aperçu corresponde "
                    + "toujours exactement au résultat final. Ne calculez plus rien côté Front.",
            tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aperçu renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallPreviewDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/residence/{residenceId}/exceptional-call-preview")
    public ResponseEntity<ExceptionalCallPreviewDTO> previewExceptionalCall(
            @PathVariable Long residenceId,
            @RequestParam(required = false) BigDecimal totalAmount,
            @RequestParam(required = false) RepartitionMode repartitionMode) {
        return ResponseEntity.ok(chargeService.previewExceptionalCall(residenceId, totalAmount, repartitionMode));
    }

    @Operation(summary = "Créer un Appel Exceptionnel — Section 1 (Informations générales)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Appel exceptionnel créé avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cette résidence ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/exceptional-calls")
    public ResponseEntity<ExceptionalCallDetailDTO> createExceptionalCall(@RequestBody @Valid CreateExceptionalCallDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeService.createExceptionalCall(dto));
    }

    @Operation(summary = "Compléter les informations financières d'un appel exceptionnel — Section 2", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Informations financières mises à jour avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallDetailDTO.class))),
            @ApiResponse(responseCode = "400", description = "Données invalides (ex: montants personnalisés manquants ou n'égalant pas le total)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cet appel exceptionnel ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel exceptionnel introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/exceptional-calls/{id}/financial-info")
    public ResponseEntity<ExceptionalCallDetailDTO> updateExceptionalCallFinancialInfo(
            @PathVariable Long id,
            @RequestBody @Valid UpdateExceptionalCallFinancialDTO dto) {
        return ResponseEntity.ok(chargeService.updateExceptionalCallFinancialInfo(id, dto));
    }

    @Operation(summary = "Activer un appel exceptionnel — Section 3 (Validation & Documents)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appel exceptionnel activé avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallDetailDTO.class))),
            @ApiResponse(responseCode = "400", description = "Complétez les informations financières avant d'activer",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cet appel exceptionnel ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel exceptionnel introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/exceptional-calls/{id}/activate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExceptionalCallDetailDTO> activateExceptionalCall(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean requiresAgValidation,
            @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {
        return ResponseEntity.ok(chargeService.activateExceptionalCall(id, requiresAgValidation, documents));
    }

    @Operation(summary = "Lister les appels exceptionnels du syndic", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallListResponse.class)))
    })
    @GetMapping("/exceptional-calls")
    public ResponseEntity<ExceptionalCallListResponse> getExceptionalCalls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallsForSyndic(page, size));
    }

    @Operation(summary = "Vue d'ensemble d'un appel exceptionnel (onglet 1)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vue d'ensemble renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallOverviewDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cet appel exceptionnel ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel exceptionnel introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/exceptional-calls/{id}/overview")
    public ResponseEntity<ExceptionalCallOverviewDTO> getExceptionalCallOverview(@PathVariable Long id) {
        return ResponseEntity.ok(chargeService.getExceptionalCallOverview(id));
    }

    @Operation(summary = "Répartition d'un appel exceptionnel entre copropriétaires (onglet 2)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Répartition renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallItemDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cet appel exceptionnel ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel exceptionnel introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/exceptional-calls/{id}/repartition")
    public ResponseEntity<Page<ExceptionalCallItemDetailDTO>> getExceptionalCallRepartition(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallRepartition(id, page, size));
    }

    @Operation(summary = "Paiements reçus pour un appel exceptionnel (onglet 3)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallPaymentDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cet appel exceptionnel ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel exceptionnel introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/exceptional-calls/{id}/payments")
    public ResponseEntity<Page<ExceptionalCallPaymentDTO>> getExceptionalCallPayments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallPayments(id, page, size));
    }

    @Operation(summary = "Documents rattachés à un appel exceptionnel (onglet 4)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallDocumentDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cet appel exceptionnel ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel exceptionnel introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/exceptional-calls/{id}/documents")
    public ResponseEntity<Page<ExceptionalCallDocumentDTO>> getExceptionalCallDocuments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallDocuments(id, page, size));
    }

    @Operation(summary = "Historique des événements d'un appel exceptionnel (onglet 5)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = ExceptionalCallHistoryDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cet appel exceptionnel ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel exceptionnel introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/exceptional-calls/{id}/history")
    public ResponseEntity<Page<ExceptionalCallHistoryDTO>> getExceptionalCallHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getExceptionalCallHistory(id, page, size));
    }

    @Operation(summary = "Clôturer un appel exceptionnel", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Appel exceptionnel clôturé avec succès"),
            @ApiResponse(responseCode = "400", description = "Cet appel exceptionnel est déjà clôturé",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cet appel exceptionnel ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Appel exceptionnel introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/exceptional-calls/{id}/close")
    public ResponseEntity<Void> closeExceptionalCall(@PathVariable Long id) {
        chargeService.closeExceptionalCall(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Recherche d'équipements communs pour autocomplétion des postes budgétaires", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = CommonFacilitySuggestionDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = PaymentListResponse.class)))
    })
    @GetMapping("/payments")
    public ResponseEntity<PaymentListResponse> getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(chargeService.getPaymentsForSyndic(page, size, search));
    }

    @Operation(summary = "Liste des impayés du syndic (toutes résidences)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = UnpaidListResponse.class)))
    })
    @GetMapping("/unpaid")
    public ResponseEntity<UnpaidListResponse> getUnpaid(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getUnpaidForSyndic(page, size));
    }

    @Operation(summary = "Relancer un copropriétaire pour une charge impayée précise", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Relance envoyée avec succès"),
            @ApiResponse(responseCode = "400", description = "Cette charge est déjà soldée",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à relancer ce copropriétaire",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Ligne de charge introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/unpaid/{chargeCallItemId}/remind")
    public ResponseEntity<Void> remindUnpaidItem(@PathVariable Long chargeCallItemId) {
        chargeService.remindUnpaidItem(chargeCallItemId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Relancer tous les copropriétaires en impayé", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relances envoyées avec succès (nombre de copropriétaires relancés)")
    })
    @PatchMapping("/unpaid/remind-all")
    public ResponseEntity<Integer> remindAllUnpaid() {
        return ResponseEntity.ok(chargeService.remindAllUnpaid());
    }

    //=========================================================================
    // DASHBOARD "GESTION DES CHARGES"
    // =========================================================================

    @Operation(summary = "Dashboard 'Gestion des charges'", description = "KPIs globaux + graphiques (encaissement mensuel, répartition des postes)", tags = {"Syndic - Charges"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = ChargeDashboardDTO.class)))
    })
    @GetMapping("/dashboard")
    public ResponseEntity<ChargeDashboardDTO> getChargeDashboard(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(chargeService.getChargeDashboard(residenceId));
    }
}
