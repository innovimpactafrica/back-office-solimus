package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.charge.*;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.services.syndic.charge.ChargeService;
import com.example.solimus.services.syndic.residence.SyndicResidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "Détail d'un budget", description = "Récupère le détail complet d'un budget avec la répartition par copropriétaire")
    @GetMapping("/{budgetId}")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<BudgetDetailDTO> getBudgetDetail(@PathVariable Long budgetId) {
        return ResponseEntity.ok(chargeService.getBudgetDetail(budgetId));
    }
}
