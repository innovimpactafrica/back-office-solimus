package com.example.solimus.controllers;

import com.example.solimus.dtos.intervention.*;
import com.example.solimus.dtos.residence.PropertyDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.services.coproprietaire.OwnerInterventionService;
import com.example.solimus.services.minio.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/owner/interventions")
@RequiredArgsConstructor
@Tag(name = "3.g Copropriétaire - Interventions", description = "Gestion des incidents par le copropriétaire")
public class OwnerInterventionController {

    private final OwnerInterventionService interventionService;
    private final MinioService minioService;

    @Operation(summary = "Lister toutes les spécialités")
    @GetMapping("/specialties")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<com.example.solimus.dtos.admin.SpecialtyDTO>> getAllSpecialties() {
        return ResponseEntity.ok(interventionService.getAllSpecialties());
    }

    @Operation(summary = "Lister les parties communes d'une résidence")
    @GetMapping("/residences/{residenceId}/common-facilities")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<com.example.solimus.dtos.residence.CommonFacilityDTO>> getCommonFacilitiesByResidence(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(interventionService.getCommonFacilitiesByResidence(residenceId));
    }

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
            @RequestParam("residenceId") Long residenceId,
            @RequestParam(value = "propertyId", required = false) Long propertyId,
            @RequestParam("specialtyId") Long specialtyId,
            @RequestParam("locationType") String locationType,
            @RequestParam(value = "managementMode", required = false) String managementMode,
            @RequestParam("urgencyLevel") String urgencyLevel,
            @Parameter(description = "Photos de l'incident", array = @ArraySchema(schema = @Schema(type = "string", format = "binary")))
            @RequestPart(value = "photos", required = false) MultipartFile[] photos) {

        CreateOwnerInterventionRequestDTO dto = CreateOwnerInterventionRequestDTO.builder()
                .title(title)
                .description(description)
                .residenceId(residenceId)
                .propertyId(propertyId)
                .specialtyId(specialtyId)
                .locationType(com.example.solimus.enums.IncidentLocationType.valueOf(locationType))
                .managementMode(managementMode != null ? com.example.solimus.enums.InterventionManagementMode.valueOf(managementMode) : null)
                .urgencyLevel(com.example.solimus.enums.UrgencyLevel.valueOf(urgencyLevel))
                .build();
        
        List<String> photoUrls = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile photo : photos) {
                if (photo != null && !photo.isEmpty()) {
                    String photoUrl = minioService.uploadFile(photo, "interventions");
                    if (photoUrl == null) {
                        throw new BadRequestException("Erreur lors de l'upload d'une photo");
                    }
                    photoUrls.add(photoUrl);
                }
            }
        }

        return ResponseEntity.status(201)
                .body(interventionService.createIntervention(dto, photoUrls));
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

    @Operation(summary = "Nombre de devis reçus pour une intervention")
    @GetMapping("/{interventionId}/quotes/count")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Map<String, Integer>> getQuotesCount(
            @PathVariable Long interventionId) {
        return ResponseEntity.ok(
            Map.of("total", interventionService.getQuotesCount(interventionId))
        );
    }

    @Operation(summary = "Lister les devis reçus pour une intervention")
    @GetMapping("/{interventionId}/quotes")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<CoOwnerQuoteCardDTO>> getQuotesByIntervention(
            @PathVariable Long interventionId) {
        return ResponseEntity.ok(
            interventionService.getQuotesByIntervention(interventionId)
        );
    }

    @Operation(summary = "Détail d'un devis")
    @GetMapping("/{interventionId}/quotes/{quoteId}")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<CoOwnerQuoteDetailDTO> getQuoteDetail(
            @PathVariable Long interventionId,
            @PathVariable Long quoteId) {
        return ResponseEntity.ok(
            interventionService.getQuoteDetail(interventionId, quoteId)
        );
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
