package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.signalement.*;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.services.syndic.signalement.SignalementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/syndic/signalements")
@RequiredArgsConstructor
@Tag(name = "Syndic - Signalements", description = "Gestion des signalements par le syndic")
public class SyndicSignalementController {

    private final SignalementService signalementService;

    // =========================================================================
    // DASHBOARD
    // =========================================================================

    @Operation(summary = "Dashboard des signalements", description = "Retourne les 4 KPIs (total, en cours, traités, en attente)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/dashboard")
    public ResponseEntity<SignalementDashboardDTO> getDashboard() {
        return ResponseEntity.ok(signalementService.getDashboard());
    }

    // =========================================================================
    // LISTER LES SIGNALEMENTS
    // =========================================================================

    @Operation(summary = "Lister les signalements (syndic)", description = "Liste paginée avec recherche et filtres")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
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
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/{id}")
    public ResponseEntity<SyndicSignalementDetailDTO> getSignalementDetail(@PathVariable Long id) {
        return ResponseEntity.ok(signalementService.getSignalementDetailForSyndic(id));
    }

    // =========================================================================
    // RÉSOUDRE SANS TRAVAUX
    // =========================================================================

    @Operation(summary = "Résoudre un signalement sans travaux")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
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
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/{id}/convert-to-work")
    public ResponseEntity<Long> convertToWork(
            @PathVariable Long id, @Valid @RequestBody ConvertToWorkDTO dto) {
        return ResponseEntity.ok(signalementService.convertToWork(id, dto));
    }
}