package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.intervention.CoOwnerQuoteCardDTO;
import com.example.solimus.dtos.owner.charge.OwnerResidenceDTO;
import com.example.solimus.dtos.syndic.travaux.PayDepositDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.owner.travaux.ValiderTravauxDTO;
import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.owner.travaux.BalanceSummaryDTO;
import com.example.solimus.dtos.owner.travaux.CoOwnerQuoteDetailDTO;
import com.example.solimus.dtos.owner.travaux.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.owner.charge.OwnerChargeService;
import com.example.solimus.services.owner.travaux.ownerTravauxService;
import com.example.solimus.services.syndic.settings.SyndicSettingsService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/owner/travaux")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_COPROPRIETAIRE')")
@Tag(name = "Copropriétaire - Travaux", description = "Gestion des demandes de travaux du copropriétaire")
public class OwnerTravauxController {

    private final SyndicSettingsService syndicParametreService;
    private final ownerTravauxService ownerTraveauxService;
    private final MinioService minioService;
    private final OwnerChargeService ownerChargeService;

    @Operation(summary = "Lister toutes les spécialités disponibles")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SpecialtyDTO.class)))
    })
    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyDTO>> getAllSpecialties() {
        return ResponseEntity.ok(syndicParametreService.getAllSpecialties(0, 100).getContent());
    }

    @Operation(summary = "Lister mes résidences")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerResidenceDTO.class)))
    })
    @GetMapping("/residences")
    public ResponseEntity<List<OwnerResidenceDTO>> getMyResidences() {
        return ResponseEntity.ok(ownerChargeService.getMyResidences());
    }

    @Operation(summary = "Lister les parties communes d'une résidence")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = CommonFacilityDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'avez pas de bien dans cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/residences/{residenceId}/common-facilities")
    public ResponseEntity<List<CommonFacilityDTO>> getCommonFacilitiesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(ownerTraveauxService.getCommonFacilitiesByResidence(residenceId));
    }

    @Operation(summary = "Lister mes biens dans une résidence")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = PropertyDTO.class)))
    })
    @GetMapping("/residences/{residenceId}/properties")
    public ResponseEntity<List<PropertyDTO>> getMyPropertiesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(ownerTraveauxService.getMyPropertiesByResidence(residenceId));
    }

    @Operation(summary = "Créer une demande d'intervention (copropriétaire)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Demande de travaux créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Données incohérentes avec le type de localisation (bien/équipement commun manquant ou en trop), "
                    + "ou équipement commun n'appartenant pas à la résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'avez pas de bien dans cette résidence, ou ce bien ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence, spécialité, bien ou équipement commun introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/interventions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createIntervention(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam Long residenceId,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long commonFacilityId,
            @RequestParam Long specialtyId,
            @RequestParam IncidentLocationType locationType,
            @RequestParam(required = false) InterventionManagementMode managementMode,
            @RequestParam UrgencyLevel urgencyLevel,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {

        // Upload chaque photo vers MinIO et récupère leurs URLs
        List<String> photoUrls = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile photo : photos) {
                String url = minioService.uploadFile(photo, "interventions");
                photoUrls.add(url);
            }
        }

        // Construit le DTO à partir des paramètres reçus, pour la couche service
        CreateOwnerInterventionRequestDTO dto = CreateOwnerInterventionRequestDTO.builder()
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

        ownerTraveauxService.createIntervention(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    @Operation(summary = "Lister mes demandes de travaux (recherche + filtres + pagination)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerInterventionDTO.class)))
    })
    @GetMapping("/interventions")
    public ResponseEntity<OwnerInterventionDTO> getMyInterventions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ownerTraveauxService.getMyInterventions(search, status, residenceId, page, size));
    }

    @Operation(summary = "Détail d'une intervention")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerInterventionDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à voir cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/interventions/{interventionId}")
    public ResponseEntity<OwnerInterventionDetailDTO> getInterventionDetail(
            @Parameter(description = "ID de l'intervention")
            @PathVariable Long interventionId) {
        return ResponseEntity.ok(ownerTraveauxService.getInterventionDetail(interventionId));
    }

    @Operation(summary = "Lister les devis d'une intervention")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = CoOwnerQuoteCardDTO.class))),
            @ApiResponse(responseCode = "403", description = "Accès non autorisé à cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/interventions/{interventionId}/quotes")
    public ResponseEntity<Page<CoOwnerQuoteCardDTO>> getQuotesByIntervention(
            @Parameter(description = "ID de l'intervention")
            @PathVariable Long interventionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ownerTraveauxService.getQuotesByIntervention(interventionId, page, size));
    }

    @Operation(summary = "Détail d'un devis")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = CoOwnerQuoteDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Accès non autorisé à cette intervention, ou les devis de cette intervention sont gérés par le syndic",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention ou devis introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/interventions/{interventionId}/quotes/{quoteId}")
    public ResponseEntity<CoOwnerQuoteDetailDTO> getQuoteDetail(
            @Parameter(description = "ID de l'intervention")
            @PathVariable Long interventionId,
            @Parameter(description = "ID du devis")
            @PathVariable Long quoteId) {
        return ResponseEntity.ok(ownerTraveauxService.getQuoteDetail(interventionId, quoteId));
    }

    @Operation(summary = "Accepter un devis")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devis accepté avec succès"),
            @ApiResponse(responseCode = "400", description = "Cette intervention a déjà un prestataire assigné, ce devis n'appartient pas à cette demande, "
                    + "ou seul un devis envoyé et en attente peut être accepté",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accepter un devis sur cette demande, ou les devis de cette intervention sont gérés par le syndic",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention ou devis introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/interventions/{interventionId}/quotes/{quoteId}/accept")
    public ResponseEntity<String> acceptQuote(
            @Parameter(description = "ID de l'intervention")
            @PathVariable Long interventionId,
            @Parameter(description = "ID du devis à accepter")
            @PathVariable Long quoteId) {
        ownerTraveauxService.acceptQuote(interventionId, quoteId);
        return ResponseEntity.ok("Devis accepté avec succès");
    }

    @Operation(summary = "Payer un acompte")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Acompte payé avec succès",
                    content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Un acompte a déjà été versé, un paiement est déjà en cours, ou aucun prestataire n'est sélectionné pour cette demande",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Accès non autorisé à cette intervention, ou cette intervention est gérée par le syndic",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/interventions/{interventionId}/deposit")
    public ResponseEntity<PaymentResponseDTO> payDeposit(
            @Parameter(description = "ID de l'intervention")
            @PathVariable Long interventionId,
            @RequestBody @Valid PayDepositDTO dto) {
        return ResponseEntity.ok(ownerTraveauxService.payDeposit(interventionId, dto));
    }

    @Operation(summary = "Récapitulatif financier avant paiement du solde (montant devis, acompte versé, solde restant)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Récapitulatif renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = BalanceSummaryDTO.class))),
            @ApiResponse(responseCode = "403", description = "Accès non autorisé à cette intervention, ou cette intervention est gérée par le syndic",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/interventions/{interventionId}/balance-summary")
    public ResponseEntity<BalanceSummaryDTO> getBalanceSummary(
            @Parameter(description = "ID de l'intervention")
            @PathVariable Long interventionId) {
        return ResponseEntity.ok(ownerTraveauxService.getBalanceSummary(interventionId));
    }

    @Operation(summary = "Valider les travaux et payer le solde")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solde payé et travaux validés avec succès",
                    content = @Content(schema = @Schema(implementation = PaymentResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Les travaux ne sont pas encore terminés, le solde a déjà été versé, ou un paiement est déjà en cours",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Accès non autorisé à cette intervention, ou cette intervention est gérée par le syndic",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/interventions/{interventionId}/balance")
    public ResponseEntity<PaymentResponseDTO> validateAndPayBalance(
            @Parameter(description = "ID de l'intervention")
            @PathVariable Long interventionId,
            @RequestBody @Valid ValiderTravauxDTO dto) {
        return ResponseEntity.ok(ownerTraveauxService.validateAndPayBalance(interventionId, dto));
    }

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
        ownerTraveauxService.createReview(interventionId, dto);
        return ResponseEntity.ok("Avis créé avec succès");
    }
}
