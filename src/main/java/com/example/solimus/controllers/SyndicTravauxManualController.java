package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicHistoryItemDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicTravauxDetailDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicTravauxListResponse;
import com.example.solimus.dtos.syndic.travaux.TravauxDashboardDTO;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.services.syndic.travaux.SyndicTravauxManualService;
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
@RequestMapping("/api/syndic/travaux-manual")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SYNDIC') and @planFeatureGuard.hasFeature('INCIDENT_MANAGEMENT')")
@Tag(name = "Syndic - Travaux (flux manuel)", description = "Gestion manuelle des demandes de travaux, sans circuit prestataire")
public class SyndicTravauxManualController {

    private final SyndicTravauxManualService syndicTravauxManualService;

    // =========================================================================
    // DASHBOARD (6 KPIs)
    // =========================================================================

    @Operation(summary = "Dashboard des travaux (flux manuel)")
    @GetMapping("/dashboard")
    public ResponseEntity<TravauxDashboardDTO> getDashboard() {
        return ResponseEntity.ok(syndicTravauxManualService.getDashboard());
    }

    // =========================================================================
    // LISTER LES INCIDENTS
    // =========================================================================

    @Operation(summary = "Lister les incidents travaux (flux manuel)", description = "Liste paginée avec recherche, filtre par statut et résidence")
    @GetMapping("/incidents")
    public ResponseEntity<SyndicTravauxListResponse> getIncidents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicTravauxManualService.getIncidents(search, status, residenceId, page, size));
    }

    // =========================================================================
    // VUE GÉNÉRALE
    // =========================================================================

    @Operation(summary = "Vue générale d'un incident (flux manuel)")
    @GetMapping("/{id}")
    public ResponseEntity<SyndicTravauxDetailDTO> getVueGenerale(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxManualService.getVueGenerale(id));
    }

    // =========================================================================
    // HISTORIQUE
    // =========================================================================

    @Operation(summary = "Historique complet d'un incident (flux manuel)")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<SyndicHistoryItemDTO>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxManualService.getHistory(id));
    }

    // =========================================================================
    // ACTIONS MANUELLES DE PROGRESSION
    // =========================================================================

    @Operation(summary = "Marquer la demande comme prise en charge", description = "PENDING → STARTED")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande prise en charge avec succès"),
            @ApiResponse(responseCode = "400", description = "Cette demande n'est pas en attente",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{id}/start")
    public ResponseEntity<Void> markAsInProgress(
            @PathVariable Long id, @RequestParam(required = false) String note) {
        syndicTravauxManualService.markAsInProgress(id, note);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Marquer les travaux comme terminés", description = "STARTED → FINISHED")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Travaux marqués comme terminés avec succès"),
            @ApiResponse(responseCode = "400", description = "Cette demande n'est pas en cours",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{id}/finish")
    public ResponseEntity<Void> markAsFinished(
            @PathVariable Long id, @RequestParam(required = false) String note) {
        syndicTravauxManualService.markAsFinished(id, note);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Clôturer définitivement la demande", description = "FINISHED → FINAL_VALIDATION")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande clôturée avec succès"),
            @ApiResponse(responseCode = "400", description = "Cette demande n'est pas encore terminée",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{id}/close")
    public ResponseEntity<Void> closeIntervention(
            @PathVariable Long id, @RequestParam(required = false) String closingNote) {
        syndicTravauxManualService.closeIntervention(id, closingNote);
        return ResponseEntity.ok().build();
    }
}
