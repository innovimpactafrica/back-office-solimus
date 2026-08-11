package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.dashboard.*;
import com.example.solimus.services.admin.dashboard.AdminDashboardService;
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
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @Operation(summary = "KPIs du dashboard admin global (syndics/prestataires actifs, résidences, copropriétaires, abonnements, revenus)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPIs renvoyés avec succès",
                    content = @Content(schema = @Schema(implementation = AdminDashboardKpiDTO.class)))
    })
    @GetMapping("/kpis")
    public ResponseEntity<AdminDashboardKpiDTO> getDashboardKpis() {
        return ResponseEntity.ok(dashboardService.getDashboardKpis());
    }

    @Operation(summary = "Évolution des revenus (Syndic + Prestataire) mois par mois, pour l'année demandée (année en cours par défaut)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Évolution renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = MonthlyRevenueDTO.class)))
    })
    @GetMapping("/revenue-evolution")
    public ResponseEntity<List<MonthlyRevenueDTO>> getMonthlyRevenue(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(dashboardService.getMonthlyRevenue(year));
    }

    @Operation(summary = "Dernières activités importantes de la plateforme, tous modules confondus")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = PlatformActivityRowDTO.class)))
    })
    @GetMapping("/recent-activities")
    public ResponseEntity<List<PlatformActivityRowDTO>> getRecentActivities(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentActivities(limit));
    }

    @Operation(summary = "Répartition des utilisateurs de la plateforme (Syndics, Prestataires, Copropriétaires)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Répartition renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = UserBreakdownDTO.class)))
    })
    @GetMapping("/user-breakdown")
    public ResponseEntity<UserBreakdownDTO> getUserBreakdown() {
        return ResponseEntity.ok(dashboardService.getUserBreakdown());
    }

    @Operation(summary = "Les derniers syndics enregistrés sur la plateforme")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = RecentSyndicDTO.class)))
    })
    @GetMapping("/recent-syndics")
    public ResponseEntity<List<RecentSyndicDTO>> getRecentSyndics(
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentSyndics(limit));
    }
}
