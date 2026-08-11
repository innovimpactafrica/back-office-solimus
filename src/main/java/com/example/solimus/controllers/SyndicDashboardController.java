package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.dashboard.*;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;
import com.example.solimus.services.syndic.dashboard.DashboardService;
import com.example.solimus.services.syndic.finance.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SYNDIC')")
@Tag(name = "Syndic - Tableau de Bord", description = "Tableau de bord principal du syndic")
public class SyndicDashboardController {

    private final DashboardService dashboardService;
    private final FinanceService financeService;

    // =========================================================================
    // KPIS PRINCIPAUX
    // =========================================================================

    // residenceId est optionnel : si absent, les KPIs sont calculés en mode global (toutes résidences confondues)
    @Operation(summary = "KPIs du tableau de bord (filtré par résidence, ou toutes résidences confondues si non précisé)", tags = {"Syndic - Tableau de Bord"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPIs renvoyés avec succès",
                    content = @Content(schema = @Schema(implementation = MainDashboardDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence non trouvée",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/dashboard/main")
    public ResponseEntity<MainDashboardDTO> getMainDashboard(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(dashboardService.getMainDashboard(residenceId));
    }

    // =========================================================================
    // ÉVOLUTION FINANCIÈRE
    // =========================================================================

    // residenceId est optionnel : si absent, calcule sur toutes les résidences (wallet global)
    @Operation(summary = "Graphique Trésorerie vs Appels de charges (filtré par résidence, ou toutes résidences si non précisé)", tags = {"Syndic - Tableau de Bord"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Graphique renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = TreasuryEvolutionPointDTO.class)))
    })
    @GetMapping("/dashboard/financial-evolution")
    public ResponseEntity<List<TreasuryEvolutionPointDTO>> getFinancialEvolution(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(financeService.getTreasuryEvolution(residenceId));
    }

    // =========================================================================
    // ALERTES IMPORTANTES
    // =========================================================================

    @Operation(summary = "Alertes importantes (impayés significatifs + AG à préparer), toutes résidences confondues", tags = {"Syndic - Tableau de Bord"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = AlertDTO.class)))
    })
    @GetMapping("/dashboard/alerts")
    public ResponseEntity<List<AlertDTO>> getImportantAlerts() {
        return ResponseEntity.ok(dashboardService.getImportantAlerts());
    }

    // =========================================================================
    // ACTIVITÉS RÉCENTES
    // =========================================================================

    @Operation(summary = "Dernières activités du syndic, toutes résidences confondues", tags = {"Syndic - Tableau de Bord"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ActivityRowDTO.class)))
    })
    @GetMapping("/dashboard/recent-activities")
    public ResponseEntity<List<ActivityRowDTO>> getRecentActivities(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentActivities(limit));
    }

    // =========================================================================
    // INCIDENTS RÉCENTS
    // =========================================================================

    @Operation(summary = "Derniers incidents gérés par le syndic, toutes résidences confondues", tags = {"Syndic - Tableau de Bord"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = RecentIncidentDTO.class)))
    })
    @GetMapping("/dashboard/recent-incidents")
    public ResponseEntity<List<RecentIncidentDTO>> getRecentIncidents(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentIncidents(limit));
    }

    // =========================================================================
    // LISTE DES RÉSIDENCES (dropdown de sélection)
    // =========================================================================

    @Operation(summary = "Lister mes résidences", description = "Retourne la liste des résidences du syndic (id + nom), pour peupler le dropdown de sélection", tags = {"Syndic - Tableau de Bord"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicResidenceDTO.class)))
    })
    @GetMapping("/dashboard/residences")
    public ResponseEntity<List<SyndicResidenceDTO>> getMyResidencesForDropdown() {
        return ResponseEntity.ok(dashboardService.getMyResidencesForDropdown());
    }
}