package com.example.solimus.controllers;

import com.example.solimus.dtos.intervention.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.intervention.NearbyProviderDTO;
import com.example.solimus.dtos.intervention.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.intervention.OwnerInterventionSummaryDTO;
import com.example.solimus.dtos.intervention.SyndicQuoteDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import com.example.solimus.services.coproprietaire.OwnerInterventionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/owner/interventions")
@RequiredArgsConstructor
@Tag(name = "Owner - Interventions", description = "Gestion des incidents par le copropriétaire")
public class OwnerInterventionController {

    private final OwnerInterventionService interventionService;

    @Operation(summary = "Trouver les prestataires proches de ma résidence")
    @GetMapping("/nearby-providers")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<NearbyProviderDTO>> findNearbyProviders(
            @Parameter(description = "ID de la spécialité")
            @RequestParam Long specialtyId) {
        return ResponseEntity.ok(interventionService.findNearbyProviders(specialtyId));
    }

    @Operation(summary = "Créer une nouvelle demande d'intervention")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<OwnerInterventionDetailDTO> createIntervention(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("specialtyId") Long specialtyId,
            @RequestParam("targetProviderIds") List<Long> targetProviderIds,
            @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
        
        CreateOwnerInterventionRequestDTO dto = CreateOwnerInterventionRequestDTO.builder()
                .title(title)
                .description(description)
                .specialtyId(specialtyId)
                .targetProviderIds(targetProviderIds)
                .build();
        
        // Pour les photos, on passera une liste vide pour l'instant
        // L'upload des photos sera géré séparément ou via MinioService
        return ResponseEntity.status(201)
                .body(interventionService.createIntervention(dto, List.of()));
    }

    @Operation(summary = "Lister mes interventions")
    @GetMapping
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<OwnerInterventionSummaryDTO>> getMyInterventions() {
        return ResponseEntity.ok(interventionService.getMyInterventions());
    }

    @Operation(summary = "Détail d'une intervention")
    @GetMapping("/{interventionId}")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<OwnerInterventionDetailDTO> getInterventionDetail(
            @Parameter(description = "ID de l'intervention")
            @PathVariable Long interventionId) {
        return ResponseEntity.ok(interventionService.getInterventionDetail(interventionId));
    }

    // ==================== GESTION DES DEVIS ====================

    @Operation(summary = "Lister les devis reçus pour une intervention")
    @GetMapping("/{interventionId}/quotes")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<SyndicQuoteDTO>> getQuotesByIntervention(
            @PathVariable Long interventionId) {
        return ResponseEntity.ok(interventionService.getQuotesByIntervention(interventionId));
    }

    @Operation(summary = "Accepter un devis et valider le prestataire")
    @PostMapping("/{interventionId}/accept-quote/{quoteId}")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<String> acceptQuote(
            @PathVariable Long interventionId,
            @PathVariable Long quoteId) {
        interventionService.acceptQuote(interventionId, quoteId);
        return ResponseEntity.ok("Devis accepté avec succès. Le prestataire a été notifié.");
    }

    // ==================== PAIEMENTS ====================

    @Operation(summary = "Payer un acompte pour une intervention validée")
    @PostMapping("/{interventionId}/payer-acompte")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<PaymentResponseDTO> payerAcompte(
            @PathVariable Long interventionId,
            @RequestBody @Valid PayerAcompteDTO dto) {
        return ResponseEntity.ok(interventionService.payerAcompte(interventionId, dto));
    }

    @Operation(summary = "Valider les travaux et payer le solde")
    @PostMapping("/{interventionId}/valider-solde")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<PaymentResponseDTO> validerEtPayerSolde(
            @PathVariable Long interventionId,
            @RequestBody @Valid ValiderTravauxDTO dto) {
        return ResponseEntity.ok(interventionService.validerEtPayerSolde(interventionId, dto));
    }
}
