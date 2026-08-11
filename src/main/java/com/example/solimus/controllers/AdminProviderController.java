package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.provider.*;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.services.admin.provider.AdminProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/providers")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Prestataires")
public class AdminProviderController {

    private final AdminProviderService providerService;

    // ===== Dashboard =====

    @Operation(summary = "KPIs de la page \"Gestion des prestataires\"")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPIs renvoyés avec succès",
                    content = @Content(schema = @Schema(implementation = AdminProviderDashboardKpiDTO.class)))
    })
    @GetMapping("/dashboard/kpis")
    public ResponseEntity<AdminProviderDashboardKpiDTO> getDashboardKpis() {
        return ResponseEntity.ok(providerService.getDashboardKpis());
    }

    // ===== Liste des prestataires =====

    @Operation(summary = "Liste paginée des prestataires, avec recherche (entreprise, responsable, email) et filtre par statut d'abonnement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = AdminProviderListResponseDTO.class)))
    })
    @GetMapping
    public ResponseEntity<AdminProviderListResponseDTO> getAllProviders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(providerService.getAllProviders(search, status, page, size));
    }

    // ===== Détail d'un prestataire =====

    @Operation(summary = "Fiche détail d'un prestataire (en-tête, KPIs, informations générales, zones d'intervention, abonnement en cours)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucun prestataire avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{providerId}")
    public ResponseEntity<ProviderDetailDTO> getProviderDetail(@PathVariable Long providerId) {
        return ResponseEntity.ok(providerService.getProviderDetail(providerId));
    }

    @Operation(summary = "Historique d'activité assemblé de ce prestataire (compte créé, abonnements, missions terminées, avis reçus)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Activités renvoyées avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucun prestataire avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{providerId}/recent-activities")
    public ResponseEntity<List<ProviderActivityRowDTO>> getProviderRecentActivities(
            @PathVariable Long providerId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(providerService.getProviderRecentActivities(providerId, limit));
    }
}
