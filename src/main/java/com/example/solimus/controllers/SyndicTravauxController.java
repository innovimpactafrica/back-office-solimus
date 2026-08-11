package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.travaux.*;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.syndic.travaux.SyndicTravauxDashboardService;
import com.example.solimus.services.syndic.travaux.SyndicTravauxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
@PreAuthorize("hasAuthority('ROLE_SYNDIC') and @planFeatureGuard.hasFeature('INCIDENT_MANAGEMENT')")
@Tag(name = "Syndic - Travaux", description = "Gestion des travaux par le syndic")
public class SyndicTravauxController {

    private final SyndicTravauxService syndicTravauxService;
    private final MinioService minioService;
    private final SyndicTravauxDashboardService syndicTravauxDashboardService;


    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC
    // =========================================================================
    @Operation(summary = "Lister mes résidences")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicResidenceDTO.class)))
    })
    @GetMapping("/residences")
    public ResponseEntity<Page<SyndicResidenceDTO>> getMesResidences(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicTravauxService.getMesResidences(page, size));
    }

    // =========================================================================
    // LISTER LES LOTS D'UNE RÉSIDENCE
    // =========================================================================
    @Operation(summary = "Lister les lots d'une résidence")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = PropertyDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/residences/{residenceId}/properties")
    public ResponseEntity<Page<PropertyDTO>> getPropertiesByResidence(
            @PathVariable Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicTravauxService.getPropertiesByResidence(residenceId, page, size));
    }

    // =========================================================================
    // LISTER LES BIENS COMMUNS D'UNE RÉSIDENCE
    // =========================================================================
    @Operation(summary = "Lister les biens communs d'une résidence")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = CommonFacilityDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/residences/{residenceId}/common-facilities")
    public ResponseEntity<Page<CommonFacilityDTO>> getCommonFacilitiesByResidence(
            @PathVariable Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicTravauxService.getCommonFacilitiesByResidence(residenceId, page, size));
    }

    // =========================================================================
    // LISTER LES SPÉCIALITÉS
    // =========================================================================
    @Operation(summary = "Lister toutes les spécialités")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SpecialtyDTO.class)))
    })
    @GetMapping("/specialties")
    public ResponseEntity<Page<SpecialtyDTO>> getAllSpecialties(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicTravauxService.getAllSpecialties(page, size));
    }

    // =========================================================================
    // CRÉATION D'INTERVENTION
    // =========================================================================
    @Operation(summary = "Créer une demande de travaux (Avec upload Minio)", tags = {"Syndic - Travaux"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Demande de travaux créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides (ex: ni bien ni équipement commun spécifié, ou les deux à la fois, bien/équipement n'appartenant pas à cette résidence)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à créer une intervention pour cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence, spécialité, bien ou équipement commun introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
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
        dto.setPhotoUrls(photoUrls); // Stockage des URLs internes

        // Appel du service pour créer la demande de travaux — les erreurs métier remontent
        // normalement jusqu'au GlobalExceptionHandler (400/403/404), comme partout ailleurs
        syndicTravauxService.createInterventionRequest(dto);

        // Réponse 204 No Content (création réussie sans retour de données)
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // ENVOI AUX PRESTATAIRES
    // =========================================================================

    @Operation(summary = "Envoyer une demande de partie commune aux prestataires")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande envoyée avec succès"),
            @ApiResponse(responseCode = "400", description = "Cette demande n'est pas une demande de partie commune gérée par le syndic, a déjà été envoyée, ou n'est plus en attente",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à gérer cette demande",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Demande introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/interventions/{interventionId}/send-to-providers")
    public ResponseEntity<String> sendToProviders(@PathVariable Long interventionId) {
        syndicTravauxService.sendToProviders(interventionId);
        return ResponseEntity.ok("Demande envoyée aux prestataires avec succès");
    }

    // =========================================================================
    // CRÉATION AVIS
    // =========================================================================

    @Operation(summary = "Créer un avis pour une intervention terminée")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avis créé avec succès"),
            @ApiResponse(responseCode = "400", description = "Intervention non terminée, aucun prestataire sélectionné, ou avis déjà laissé",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à laisser un avis sur cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devis validé avec succès"),
            @ApiResponse(responseCode = "400", description = "Intervention non gérée par le syndic, prestataire déjà sélectionné, devis n'appartenant pas à cette intervention, ou devis non en attente",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à gérer cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention ou devis introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/interventions/{id}/quotes/{quoteId}/validate")
    public ResponseEntity<Void> validateQuote(@PathVariable Long id, @PathVariable Long quoteId) {
        syndicTravauxService.validateQuote(id, quoteId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Résumé pour le modal Acompte")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résumé renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicDepositSummaryDTO.class))),
            @ApiResponse(responseCode = "400", description = "Aucun devis n'a encore été validé pour cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/interventions/{id}/deposit-summary")
    public ResponseEntity<SyndicDepositSummaryDTO> getDepositSummary(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxService.getDepositSummary(id));
    }

    @Operation(summary = "Payer un acompte")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Acompte payé avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicPaymentResultDTO.class))),
            @ApiResponse(responseCode = "400", description = "Aucun prestataire sélectionné, acompte déjà versé, ou solde du wallet insuffisant",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à effectuer ce paiement",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/interventions/{id}/deposit")
    public ResponseEntity<SyndicPaymentResultDTO> payDeposit(
            @PathVariable Long id, @Valid @RequestBody SyndicPayDepositDTO dto) {
        return ResponseEntity.ok(syndicTravauxService.payDeposit(id, dto));
    }

    @Operation(summary = "Résumé pour le modal Paiement final")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résumé renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicBalancePaymentSummaryDTO.class))),
            @ApiResponse(responseCode = "400", description = "Les travaux ne sont pas encore terminés",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/interventions/{id}/balance-summary")
    public ResponseEntity<SyndicBalancePaymentSummaryDTO> getBalanceSummary(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxService.getBalanceSummary(id));
    }

    @Operation(summary = "Payer le solde final et clôturer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solde payé et intervention clôturée avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicPaymentResultDTO.class))),
            @ApiResponse(responseCode = "400", description = "Travaux non terminés, budget déjà clôturé, aucun solde restant, ou solde du wallet insuffisant",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à effectuer ce paiement",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/interventions/{id}/pay-balance")
    public ResponseEntity<SyndicPaymentResultDTO> payBalanceAndClose(
            @PathVariable Long id, @Valid @RequestBody SyndicPayDepositDTO dto) {
        return ResponseEntity.ok(syndicTravauxService.payBalanceAndClose(id, dto));
    }

    // =========================================================================
    // MISE À JOUR ET SUPPRESSION D'INTERVENTION
    // =========================================================================

    @Operation(summary = "Mettre à jour partiellement une demande d'intervention avec upload MinIO (uniquement si en attente de devis)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Intervention mise à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "L'intervention n'est plus en attente de devis",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à modifier cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention, résidence, bien, équipement commun ou spécialité introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping(value = "/interventions/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateIntervention(
            @PathVariable Long id,

            @Parameter(description = "Titre court de l'intervention")
            @RequestParam(value = "title", required = false) String title,

            @Parameter(description = "Description détaillée du problème")
            @RequestParam(value = "description", required = false) String description,

            @Parameter(description = "ID de la résidence concernée")
            @RequestParam(value = "residenceId", required = false) Long residenceId,

            @Parameter(description = "ID du bien concerné")
            @RequestParam(value = "propertyId", required = false) Long propertyId,

            @Parameter(description = "ID de la partie commune concernée")
            @RequestParam(value = "commonFacilityId", required = false) Long commonFacilityId,

            @Parameter(description = "ID de la spécialité requise")
            @RequestParam(value = "specialtyId", required = false) Long specialtyId,

            @Parameter(description = "Type de localisation (APPARTEMENT ou PARTIE_COMMUNE)")
            @RequestParam(value = "locationType", required = false) IncidentLocationType locationType,

            @Parameter(description = "Mode de gestion")
            @RequestParam(value = "managementMode", required = false) InterventionManagementMode managementMode,

            @Parameter(description = "Niveau d'urgence")
            @RequestParam(value = "urgencyLevel", required = false) UrgencyLevel urgencyLevel,

            @Parameter(description = "Photos du problème à remplacer")
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {

        List<String> photoUrls = null;
        if (photos != null) {
            photoUrls = new ArrayList<>();
            for (MultipartFile photo : photos) {
                String url = minioService.uploadFile(photo, "interventions");
                photoUrls.add(url);
            }
        }

        UpdateInterventionRequestDTO dto = UpdateInterventionRequestDTO.builder()
                .title(title)
                .description(description)
                .residenceId(residenceId)
                .propertyId(propertyId)
                .commonFacilityId(commonFacilityId)
                .specialtyId(specialtyId)
                .locationType(locationType)
                .managementMode(managementMode)
                .urgencyLevel(urgencyLevel)
                .photoUrls(photoUrls)
                .build();

        syndicTravauxService.updateIntervention(id, dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Supprimer une demande d'intervention (uniquement si en attente de devis)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Intervention supprimée avec succès"),
            @ApiResponse(responseCode = "400", description = "L'intervention n'est plus en attente de devis",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à supprimer cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/interventions/{id}")
    public ResponseEntity<Void> deleteIntervention(@PathVariable Long id) {
        syndicTravauxService.deleteIntervention(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ajouter des photos à une intervention existante (avec upload MinIO)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Photos ajoutées avec succès"),
            @ApiResponse(responseCode = "400", description = "L'intervention n'est plus en attente de devis",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à modifier cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/interventions/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<String>> addPhotosToIntervention(
            @PathVariable Long id,
            @Parameter(description = "Photos à ajouter")
            @RequestPart("photos") List<MultipartFile> photos) {

        List<String> photoUrls = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile photo : photos) {
                String objectKey = minioService.uploadFile(photo, "interventions");
                photoUrls.add(objectKey);
            }
        }

        syndicTravauxService.addPhotosToIntervention(id, photoUrls);
        return ResponseEntity.ok(photoUrls);
    }

    // =========================================================================
    // DASHBOARD (6 KPIs)
    // =========================================================================

    @Operation(summary = "Dashboard des travaux", description = "Retourne les 6 KPIs (ouverts, urgents, en attente devis, en cours, résolus, clôturés)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = TravauxDashboardDTO.class)))
    })
    @GetMapping("/dashboard")
    public ResponseEntity<TravauxDashboardDTO> getDashboard() {
        return ResponseEntity.ok(syndicTravauxDashboardService.getDashboard());
    }

    // =========================================================================
    // LISTER LES INCIDENTS (paginé, avec recherche et filtres)
    // =========================================================================

    @Operation(summary = "Lister les incidents travaux", description = "Liste paginée avec recherche, filtre par statut et résidence")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicTravauxListResponse.class)))
    })
    @GetMapping("/incidents")
    public ResponseEntity<SyndicTravauxListResponse> getIncidents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicTravauxDashboardService.getIncidents(search, status, residenceId, page, size));
    }

    // =========================================================================
    // ONGLET 1 — VUE GÉNÉRALE
    // =========================================================================

    @Operation(summary = "Vue générale d'un incident (onglet 1)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicTravauxDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/incidents/{id}")
    public ResponseEntity<SyndicTravauxDetailDTO> getVueGenerale(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxDashboardService.getVueGenerale(id));
    }

    // =========================================================================
    // ONGLET 2 — DEVIS
    // =========================================================================

    @Operation(summary = "Liste des devis reçus pour un incident (onglet 2)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicQuoteCardDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/incidents/{id}/quotes")
    public ResponseEntity<List<SyndicQuoteCardDTO>> getQuotes(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxDashboardService.getQuotes(id));
    }

    @Operation(summary = "Détail d'un devis précis (onglet 2 → clic)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicQuoteDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention ou devis introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/incidents/{id}/quotes/{quoteId}")
    public ResponseEntity<SyndicQuoteDetailDTO> getQuoteDetail(
            @PathVariable Long id, @PathVariable Long quoteId) {
        return ResponseEntity.ok(syndicTravauxDashboardService.getQuoteDetail(id, quoteId));
    }

    // =========================================================================
    // ONGLET 3 — INTERVENTION
    // =========================================================================

    @Operation(summary = "Données de l'onglet Intervention (onglet 3)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Données renvoyées avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicInterventionTabDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/incidents/{id}/intervention")
    public ResponseEntity<SyndicInterventionTabDTO> getInterventionTab(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxDashboardService.getInterventionTab(id));
    }

    // =========================================================================
    // ONGLET 4 — HISTORIQUE
    // =========================================================================

    @Operation(summary = "Historique complet d'un incident (onglet 4)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicHistoryItemDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/incidents/{id}/history")
    public ResponseEntity<List<SyndicHistoryItemDTO>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(syndicTravauxDashboardService.getHistory(id));
    }
}
