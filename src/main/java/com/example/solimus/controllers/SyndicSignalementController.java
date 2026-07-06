package com.example.solimus.controllers;

import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.syndic.signalement.ResoudreSignalementDTO;
import com.example.solimus.dtos.syndic.signalement.SyndicSignalementDetailDTO;
import com.example.solimus.dtos.syndic.signalement.SyndicSignalementListDTO;
import com.example.solimus.dtos.syndic.signalement.TransformerEnTravauxDTO;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.services.syndic.signalement.SignalementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "Lister les signalements (syndic)")
    @GetMapping
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<SyndicSignalementListDTO> getSignalements(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SignalementStatus status,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        return ResponseEntity.ok(
                signalementService.getSignalementsForSyndic(search, status, residenceId, page, size)
        );
    }

    @Operation(summary = "Détail d'un signalement (syndic)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/{id}")
    public ResponseEntity<SyndicSignalementDetailDTO> getSignalementDetail(@PathVariable Long id) {
        return ResponseEntity.ok(signalementService.getSignalementDetailForSyndic(id));
    }

    @Operation(summary = "Résoudre un signalement sans travaux")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/{id}/resoudre")
    public ResponseEntity<Void> resoudreSansTravaux(
            @PathVariable Long id, @RequestBody ResoudreSignalementDTO dto) {
        signalementService.resoudreSansTravaux(id, dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Transformer un signalement en demande de travaux")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/{id}/transformer-en-travaux")
    public ResponseEntity<InterventionRequestDTO> transformerEnTravaux(
            @PathVariable Long id, @RequestBody TransformerEnTravauxDTO dto) {
        return ResponseEntity.ok(signalementService.transformerEnTravaux(id, dto));
    }
}
