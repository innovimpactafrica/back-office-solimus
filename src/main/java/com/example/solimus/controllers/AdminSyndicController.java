package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.syndic.*;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.dashboard.ActivityRowDTO;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.services.admin.syndic.SyndicService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/syndics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Syndics")
public class AdminSyndicController {

    private final SyndicService syndicService;

    // ===== Création d'un syndic =====

    @Operation(summary = "Créer un nouveau syndic avec son abonnement")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Syndic créé avec succès"),
            @ApiResponse(responseCode = "400", description = "La formule choisie ne propose pas de tarif pour la durée demandée (annuel/mensuel)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Formule d'abonnement introuvable, ou rôle SYNDIC introuvable en base",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Un compte avec cet email ou ce téléphone existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping
    public ResponseEntity<CreateSyndicResponseDTO> createSyndic(@RequestBody @Valid CreateSyndicDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(syndicService.createSyndic(dto));
    }

    @Operation(summary = "Lister les formules disponibles pour la création d'un syndic")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicPlanOptionDTO.class)))
    })
    @GetMapping("/plans")
    public List<SyndicPlanOptionDTO> listAvailablePlans() {
        return syndicService.listAvailablePlans();
    }

    // ===== Dashboard =====

    @Operation(summary = "KPIs de la page \"Gestion des syndics\"")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPIs renvoyés avec succès",
                    content = @Content(schema = @Schema(implementation = AdminSyndicDashboardKpiDTO.class)))
    })
    @GetMapping("/dashboard/kpis")
    public ResponseEntity<AdminSyndicDashboardKpiDTO> getDashboardKpis() {
        return ResponseEntity.ok(syndicService.getDashboardKpis());
    }

    // ===== Liste des syndics =====

    @Operation(summary = "Liste paginée des syndics, avec recherche (nom, prénom, email, entreprise) et filtre par statut d'abonnement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = AdminSyndicListResponseDTO.class)))
    })
    @GetMapping
    public ResponseEntity<AdminSyndicListResponseDTO> getAllSyndics(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(syndicService.getAllSyndics(search, status, page, size));
    }

    // ===== Détail d'un syndic =====

    @Operation(summary = "Fiche détail d'un syndic (informations générales, KPIs, abonnement en cours)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucun syndic avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{syndicId}")
    public ResponseEntity<SyndicDetailDTO> getSyndicDetail(@PathVariable Long syndicId) {
        return ResponseEntity.ok(syndicService.getSyndicDetail(syndicId));
    }

    @Operation(summary = "Les N dernières activités de ce syndic, toutes résidences confondues")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Activités renvoyées avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucun syndic avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{syndicId}/recent-activities")
    public ResponseEntity<List<ActivityRowDTO>> getSyndicRecentActivities(
            @PathVariable Long syndicId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(syndicService.getSyndicRecentActivities(syndicId, limit));
    }

    // ===== Résidences gérées par un syndic =====

    @Operation(summary = "Liste paginée des résidences gérées par ce syndic, avec leur nombre d'alertes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucun syndic avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{syndicId}/residences")
    public ResponseEntity<Page<SyndicManagedResidenceDTO>> getSyndicResidences(
            @PathVariable Long syndicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(syndicService.getSyndicResidences(syndicId, page, size));
    }

    @Operation(summary = "Fiche détail d'une résidence gérée par ce syndic (informations générales + KPIs)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucun syndic ou aucune résidence avec ces ids, ou cette résidence n'appartient pas à ce syndic",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{syndicId}/residences/{residenceId}")
    public ResponseEntity<ResidenceDetailDTO> getResidenceDetail(
            @PathVariable Long syndicId,
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(syndicService.getResidenceDetail(syndicId, residenceId));
    }

    @Operation(summary = "Répartition paginée des biens de cette résidence par type (T1, T2, T3...)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Répartition renvoyée avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucun syndic ou aucune résidence avec ces ids, ou cette résidence n'appartient pas à ce syndic",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{syndicId}/residences/{residenceId}/property-types")
    public ResponseEntity<Page<PropertyTypeBreakdownDTO>> getResidencePropertyTypeBreakdown(
            @PathVariable Long syndicId,
            @PathVariable Long residenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(syndicService.getResidencePropertyTypeBreakdown(syndicId, residenceId, page, size));
    }

    @Operation(summary = "Les N derniers incidents (signalements) de cette résidence, du plus récent au plus ancien")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incidents renvoyés avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucun syndic ou aucune résidence avec ces ids, ou cette résidence n'appartient pas à ce syndic",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{syndicId}/residences/{residenceId}/recent-incidents")
    public ResponseEntity<List<ResidenceIncidentRowDTO>> getResidenceRecentIncidents(
            @PathVariable Long syndicId,
            @PathVariable Long residenceId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(syndicService.getResidenceRecentIncidents(syndicId, residenceId, limit));
    }
}
