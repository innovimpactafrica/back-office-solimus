package com.example.solimus.controllers;
import com.example.solimus.dtos.syndic.dashboard.*;
import com.example.solimus.services.syndic.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "Syndic - Tableau de Bord", description = "Tableau de bord principal du syndic")
public class SyndicDashboardController {

    private final DashboardService dashboardService;

    // =========================================================================
    // KPIS PRINCIPAUX
    // =========================================================================

    @Operation(summary = "KPIs du tableau de bord (filtré par résidence)", tags = {"Syndic - Tableau de Bord"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/dashboard/main")
    public ResponseEntity<MainDashboardDTO> getMainDashboard(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(dashboardService.getMainDashboard(residenceId));
    }

    // =========================================================================
    // ÉVOLUTION FINANCIÈRE
    // =========================================================================

    @Operation(summary = "Graphique Trésorerie vs Appels de charges (filtré par résidence)", tags = {"Syndic - Tableau de Bord"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/dashboard/financial-evolution")
    public ResponseEntity<List<TreasuryEvolutionPointDTO>> getFinancialEvolution(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(dashboardService.getFinancialEvolution(residenceId));
    }

    // =========================================================================
    // ALERTES IMPORTANTES
    // =========================================================================

    @Operation(summary = "Alertes importantes (impayés significatifs + AG à préparer)", tags = {"Syndic - Tableau de Bord"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/dashboard/alerts")
    public ResponseEntity<List<AlertDTO>> getImportantAlerts() {
        return ResponseEntity.ok(dashboardService.getImportantAlerts());
    }

    // =========================================================================
    // ACTIVITÉS RÉCENTES
    // =========================================================================

    @Operation(summary = "Dernières activités du syndic, toutes résidences confondues", tags = {"Syndic - Tableau de Bord"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/dashboard/recent-activities")
    public ResponseEntity<List<ActivityRowDTO>> getRecentActivities(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentActivities(limit));
    }

    // =========================================================================
    // INCIDENTS RÉCENTS
    // =========================================================================

    @Operation(summary = "Derniers incidents gérés par le syndic, toutes résidences confondues", tags = {"Syndic - Tableau de Bord"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/dashboard/recent-incidents")
    public ResponseEntity<List<RecentIncidentDTO>> getRecentIncidents(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentIncidents(limit));
    }
}