package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.owner.travaux.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.owner.travaux.OwnerTravauxManualService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

// Chemins distincts de /api/owner/travaux (flux devis/prestataire existant, non touché) —
// le nouveau Front branche sur ces nouveaux chemins pour le flux manuel.
@RestController
@RequestMapping("/api/owner/travaux-manual")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_COPROPRIETAIRE')")
@Tag(name = "Copropriétaire - Travaux (flux manuel)", description = "Demandes de travaux du copropriétaire, gérées manuellement par le syndic")
public class OwnerTravauxManualController {

    private final OwnerTravauxManualService ownerTravauxManualService;
    private final MinioService minioService;

    @Operation(summary = "Lister mes résidences")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ResidenceDTO.class)))
    })
    @GetMapping("/residences")
    public ResponseEntity<List<ResidenceDTO>> getMyResidences() {
        return ResponseEntity.ok(ownerTravauxManualService.getMyResidences());
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
        return ResponseEntity.ok(ownerTravauxManualService.getCommonFacilitiesByResidence(residenceId));
    }

    @Operation(summary = "Lister mes biens dans une résidence")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = PropertyDTO.class)))
    })
    @GetMapping("/residences/{residenceId}/properties")
    public ResponseEntity<List<PropertyDTO>> getMyPropertiesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(ownerTravauxManualService.getMyPropertiesByResidence(residenceId));
    }

    @Operation(summary = "Créer une demande d'intervention (copropriétaire)", description = "Toujours gérée manuellement par le syndic (pas de circuit prestataire)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Demande de travaux créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides (ex: équipement commun manquant ou n'appartenant pas à la résidence)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'avez pas de bien dans cette résidence, ou ce bien ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence, bien, spécialité ou équipement commun introuvable",
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
            @RequestParam UrgencyLevel urgencyLevel,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {

        List<String> photoUrls = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile photo : photos) {
                String url = minioService.uploadFile(photo, "interventions");
                photoUrls.add(url);
            }
        }

        CreateOwnerInterventionRequestDTO dto = CreateOwnerInterventionRequestDTO.builder()
                .title(title)
                .description(description)
                .residenceId(residenceId)
                .propertyId(propertyId)
                .commonFacilityId(commonFacilityId)
                .specialtyId(specialtyId)
                .locationType(locationType)
                .urgencyLevel(urgencyLevel)
                .photoUrls(photoUrls)
                .build();

        ownerTravauxManualService.createIntervention(dto);
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
        return ResponseEntity.ok(ownerTravauxManualService.getMyInterventions(search, status, residenceId, page, size));
    }

    @Operation(summary = "Détail d'une intervention")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerInterventionDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cette intervention n'appartient pas au copropriétaire connecté",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/interventions/{interventionId}")
    public ResponseEntity<OwnerInterventionDetailDTO> getInterventionDetail(@PathVariable Long interventionId) {
        return ResponseEntity.ok(ownerTravauxManualService.getInterventionDetail(interventionId));
    }
}
