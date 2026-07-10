package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.travaux.CreateInterventionRequestDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicDepositSummaryDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicPayDepositDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicBalancePaymentSummaryDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicPaymentResultDTO;
import com.example.solimus.dtos.syndic.travaux.UpdateInterventionRequestDTO;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.syndic.travaux.SyndicTravauxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/syndic/travaux")
@RequiredArgsConstructor
@Tag(name = "Syndic - Travaux", description = "Gestion des travaux par le syndic")
public class SyndicTravauxController {

    private final SyndicTravauxService syndicTravauxService;
    private final MinioService minioService;

    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC
    // =========================================================================
    @Operation(summary = "Lister mes résidences")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences")
    public ResponseEntity<List<SyndicResidenceDTO>> getMesResidences() {
        return ResponseEntity.ok(syndicTravauxService.getMesResidences());
    }

    // =========================================================================
    // LISTER LES LOTS D'UNE RÉSIDENCE
    // =========================================================================
    @Operation(summary = "Lister les lots d'une résidence")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/properties")
    public ResponseEntity<List<PropertyDTO>> getPropertiesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(syndicTravauxService.getPropertiesByResidence(residenceId));
    }

    // =========================================================================
    // LISTER LES BIENS COMMUNS D'UNE RÉSIDENCE
    // =========================================================================
    @Operation(summary = "Lister les biens communs d'une résidence")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/common-facilities")
    public ResponseEntity<List<CommonFacilityDTO>> getCommonFacilitiesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(syndicTravauxService.getCommonFacilitiesByResidence(residenceId));
    }

    // =========================================================================
    // LISTER LES SPÉCIALITÉS
    // =========================================================================
    @Operation(summary = "Lister toutes les spécialités")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyDTO>> getAllSpecialties() {
        return ResponseEntity.ok(syndicTravauxService.getAllSpecialties());
    }

    // =========================================================================
    // CRÉATION D'INTERVENTION
    // =========================================================================
    @Operation(summary = "Créer une demande de travaux (Avec upload Minio)", tags = {"Syndic - Travaux"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping(value = "/interventions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createIntervention(
            @Parameter(description = "Titre court de l'intervention (ex: Fuite d'eau)")
            @RequestParam("title") String title,

            @Parameter(description = "Description détaillée du problème")
            @RequestParam("description") String description,

            @Parameter(description = "ID de la résidence concernée")
            @RequestParam("residenceId") Long residenceId,

            @Parameter(description = "ID du bien concerné (obligatoire si APPARTEMENT)")
            @RequestParam(value = "propertyId", required = false) Long propertyId,

            @Parameter(description = "ID de la partie commune concernée (obligatoire si PARTIE_COMMUNE)")
            @RequestParam(value = "commonFacilityId", required = false) Long commonFacilityId,

            @Parameter(description = "ID de la spécialité requise (Plomberie, Électricité, etc.)")
            @RequestParam("specialtyId") Long specialtyId,

            @Parameter(description = "Type de localisation (APPARTEMENT ou PARTIE_COMMUNE)")
            @RequestParam("locationType") IncidentLocationType locationType,

            @Parameter(description = "Niveau d'urgence (LOW, MEDIUM, HIGH)")
            @RequestParam("urgencyLevel") UrgencyLevel urgencyLevel,

            @Parameter(description = "Photos du problème (JPG, PNG uniquement)")
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {


        try {

            // Upload chaque photo vers MinIO et récupère leurs URLs
            List<String> photoUrls = new ArrayList<>();
            if (photos != null) {
                for (MultipartFile photo : photos) {
                    String url = minioService.uploadFile(photo, "interventions");
                    photoUrls.add(url);
                }
            }

            // Construction du DTO avec les données reçues
            CreateInterventionRequestDTO dto = new CreateInterventionRequestDTO();
            dto.setTitle(title);
            dto.setDescription(description);
            dto.setResidenceId(residenceId);
            dto.setPropertyId(propertyId);
            dto.setCommonFacilityId(commonFacilityId);
            dto.setSpecialtyId(specialtyId);
            dto.setLocationType(locationType);
            dto.setUrgencyLevel(urgencyLevel);
            dto.setPhotoUrls(photoUrls); // Ajout des noms des photos uploadées

            // Appel du service pour créer la demande de travaux
            syndicTravauxService.createInterventionRequest(dto);

            // Réponse 204 No Content (création réussie sans retour de données)
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            // Gestion des erreurs : retour d'une erreur 500 avec le message
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création de la demande : " + e.getMessage());
        }
    }

    // =========================================================================
    // ENVOI AUX PRESTATAIRES
    // =========================================================================

    @Operation(summary = "Envoyer une demande de partie commune aux prestataires")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/interventions/{interventionId}/send-to-providers")
    public ResponseEntity<String> sendToProviders(@PathVariable Long interventionId) {
        syndicTravauxService.sendToProviders(interventionId);
        return ResponseEntity.ok("Demande envoyée aux prestataires avec succès");
    }

    // =========================================================================
    // CRÉATION AVIS
    // =========================================================================

    @Operation(summary = "Créer un avis pour une intervention terminée")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/interventions/{interventionId}/review")
    public ResponseEntity<String> createReview(
            @PathVariable Long interventionId,
            @RequestBody @Valid CreateReviewDTO dto) {
        syndicTravauxService.createReview(interventionId, dto);
        return ResponseEntity.ok("Avis créé avec succès");
    }

    // =========================================================================
    // VALIDATION DE DEVIS ET PAIEMENTS
    // =========================================================================

    @Operation(summary = "Valider un devis", description = "Valide un devis pour une intervention de partie commune, rejette les autres")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping("/interventions/{id}/quotes/{quoteId}/validate")
    public ResponseEntity<Void> validateQuote(@PathVariable Long id, @PathVariable Long quoteId) {
        syndicTravauxService.validateQuote(id, quoteId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Résumé pour le modal Acompte")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/interventions/{id}/deposit-summary")
    public ResponseEntity<SyndicDepositSummaryDTO> getDepositSummary(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxService.getDepositSummary(id));
    }

    @Operation(summary = "Payer un acompte")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/interventions/{id}/deposit")
    public ResponseEntity<SyndicPaymentResultDTO> payDeposit(
            @PathVariable Long id, @Valid @RequestBody SyndicPayDepositDTO dto) {
        return ResponseEntity.ok(syndicTravauxService.payDeposit(id, dto));
    }

    @Operation(summary = "Résumé pour le modal Paiement final")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/interventions/{id}/balance-summary")
    public ResponseEntity<SyndicBalancePaymentSummaryDTO> getBalanceSummary(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxService.getBalanceSummary(id));
    }

    @Operation(summary = "Payer le solde final et clôturer")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/interventions/{id}/pay-balance")
    public ResponseEntity<SyndicPaymentResultDTO> payBalanceAndClose(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxService.payBalanceAndClose(id));
    }

    // =========================================================================
    // MISE À JOUR ET SUPPRESSION D'INTERVENTION
    // =========================================================================

    @Operation(summary = "Mettre à jour partiellement une demande d'intervention (uniquement si en attente de devis)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping("/interventions/{id}")
    public ResponseEntity<Void> updateIntervention(
            @PathVariable Long id,
            @RequestBody @Valid UpdateInterventionRequestDTO dto) {
        syndicTravauxService.updateIntervention(id, dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Supprimer une demande d'intervention (uniquement si en attente de devis)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/interventions/{id}")
    public ResponseEntity<Void> deleteIntervention(@PathVariable Long id) {
        syndicTravauxService.deleteIntervention(id);
        return ResponseEntity.noContent().build();
    }
}
