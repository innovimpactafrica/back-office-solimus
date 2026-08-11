package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.signalement.*;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.services.syndic.signalement.SignalementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/syndic/signalements")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SYNDIC') and @planFeatureGuard.hasFeature('REPORT_MANAGEMENT')")
@Tag(name = "Syndic - Signalements", description = "Gestion des signalements par le syndic")
public class SyndicSignalementController {

    private final SignalementService signalementService;

    // =========================================================================
    // DASHBOARD
    // =========================================================================

    @Operation(summary = "Dashboard des signalements", description = "Retourne les 4 KPIs (total, en cours, traités, en attente)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SignalementDashboardDTO.class)))
    })
    @GetMapping("/dashboard")
    public ResponseEntity<SignalementDashboardDTO> getDashboard() {
        return ResponseEntity.ok(signalementService.getDashboard());
    }

    // =========================================================================
    // LISTER LES SIGNALEMENTS
    // =========================================================================

    @Operation(summary = "Lister les signalements (syndic)", description = "Liste paginée avec recherche et filtres")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicSignalementListResponse.class)))
    })
    @GetMapping
    public ResponseEntity<SyndicSignalementListResponse> getSignalements(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SignalementStatus status,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(signalementService.getSignalementsForSyndic(search, status, residenceId, page, size));
    }

    // =========================================================================
    // DÉTAIL D'UN SIGNALEMENT
    // =========================================================================

    @Operation(summary = "Détail d'un signalement (syndic)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicSignalementDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à ce signalement",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Signalement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<SyndicSignalementDetailDTO> getSignalementDetail(@PathVariable Long id) {
        return ResponseEntity.ok(signalementService.getSignalementDetailForSyndic(id));
    }

    // =========================================================================
    // RÉSOUDRE SANS TRAVAUX
    // =========================================================================

    @Operation(summary = "Résoudre un signalement sans travaux")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signalement résolu avec succès"),
            @ApiResponse(responseCode = "400", description = "Ce signalement est déjà résolu, ou a déjà été transformé en demande de travaux",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à traiter ce signalement",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Signalement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveWithoutWork(
            @PathVariable Long id, @Valid @RequestBody ResolveSignalementDTO dto) {
        signalementService.resolveWithoutWork(id, dto);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // TRANSFORMER EN TRAVAUX
    // =========================================================================

    @Operation(summary = "Transformer un signalement en demande de travaux")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signalement transformé en demande de travaux avec succès (id de l'intervention créée)"),
            @ApiResponse(responseCode = "400", description = "Ce signalement est déjà résolu, ou a déjà été transformé en demande de travaux",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à traiter ce signalement",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Signalement ou spécialité introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{id}/convert-to-work")
    public ResponseEntity<Long> convertToWork(
            @PathVariable Long id, @Valid @RequestBody ConvertToWorkDTO dto) {
        return ResponseEntity.ok(signalementService.convertToWork(id, dto));
    }
}