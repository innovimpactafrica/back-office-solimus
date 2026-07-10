package com.example.solimus.controllers;

import com.example.solimus.dtos.provider.travaux.ProviderTravauxDetailDTO;
import com.example.solimus.dtos.provider.travaux.ProviderTravauxPageDTO;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.services.provider.travaux.ProviderTravauxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@RestController
@RequestMapping("/api/provider/travaux")
@RequiredArgsConstructor
@Validated
@Tag(name = "Prestataire - Travaux", description = "Travaux du prestataire (demandes dont le devis a été accepté)")
public class ProviderTravauxController {

    private final ProviderTravauxService providerTravauxService;

    @Operation(summary = "Lister mes travaux (devis accepté, en cours, terminés, clôturés)")
    @PreAuthorize("hasRole('ROLE_PRESTATAIRE')")
    @GetMapping
    public ResponseEntity<ProviderTravauxPageDTO> getMyWorks(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(providerTravauxService.getMyWorks(search, status, page, size));
    }

    @Operation(summary = "Voir le détail d'une intervention assignée")
    @PreAuthorize("hasRole('ROLE_PRESTATAIRE')")
    @GetMapping("/{id}")
    public ResponseEntity<ProviderTravauxDetailDTO> getWorkDetails(@PathVariable Long id) {
        return ResponseEntity.ok(providerTravauxService.getWorkDetails(id));
    }

    @Operation(summary = "Démarrer une intervention (passer de QUOTE_VALIDATED à STARTED)")
    @PreAuthorize("hasRole('ROLE_PRESTATAIRE')")
    @PostMapping("/{id}/start")
    public ResponseEntity<String> startIntervention(@PathVariable Long id) {
        providerTravauxService.startIntervention(id);
        return ResponseEntity.ok("Les travaux ont été démarrés avec succès.");
    }

    @Operation(summary = "Terminer une intervention (passer de STARTED à FINISHED)")
    @PreAuthorize("hasRole('ROLE_PRESTATAIRE')")
    @PostMapping(value = "/{id}/finish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> finishIntervention(
            @PathVariable Long id,
            @RequestParam(required = false) String commentaire,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        providerTravauxService.finishIntervention(id, commentaire, photos);
        return ResponseEntity.ok("L'intervention a été marquée comme terminée.");
    }
}
