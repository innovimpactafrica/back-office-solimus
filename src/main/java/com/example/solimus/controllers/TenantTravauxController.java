package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.tenant.travaux.CreateTenantInterventionRequestDTO;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.tenant.travaux.TenantTravauxService;
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

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_LOCATAIRE')")
@Tag(name = "Locataire - Travaux", description = "Demandes de travaux du locataire, gérées manuellement par le syndic")
public class TenantTravauxController {

    private final TenantTravauxService tenantTravauxService;
    private final MinioService minioService;

    // =========================================================================
    // LISTER LES PARTIES COMMUNES DE MA RÉSIDENCE
    // =========================================================================

    @Operation(summary = "Lister les parties communes de ma résidence")
    @GetMapping("/common-facilities")
    public ResponseEntity<List<CommonFacilityDTO>> getCommonFacilities() {
        return ResponseEntity.ok(tenantTravauxService.getCommonFacilities());
    }

    // =========================================================================
    // CRÉER UNE DEMANDE DE TRAVAUX
    // =========================================================================

    @Operation(summary = "Créer une demande de travaux", description = "Toujours gérée manuellement par le syndic (pas de circuit prestataire)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Demande de travaux créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides (ex: commonFacilityId manquant ou n'appartenant pas à la résidence)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Aucun bien n'est assigné à ce compte locataire",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Spécialité ou équipement commun introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/interventions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createIntervention(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam Long specialtyId,
            @RequestParam IncidentLocationType locationType,
            @RequestParam UrgencyLevel urgencyLevel,
            @RequestParam(required = false) Long commonFacilityId,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {

        List<String> photoUrls = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile photo : photos) {
                String url = minioService.uploadFile(photo, "interventions");
                photoUrls.add(url);
            }
        }

        CreateTenantInterventionRequestDTO dto = CreateTenantInterventionRequestDTO.builder()
                .title(title)
                .description(description)
                .specialtyId(specialtyId)
                .locationType(locationType)
                .urgencyLevel(urgencyLevel)
                .commonFacilityId(commonFacilityId)
                .photoUrls(photoUrls)
                .build();

        tenantTravauxService.createIntervention(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // =========================================================================
    // LISTER MES DEMANDES DE TRAVAUX
    // =========================================================================

    @Operation(summary = "Lister mes demandes de travaux (recherche + filtre statut + pagination)")
    @GetMapping("/interventions")
    public ResponseEntity<OwnerInterventionDTO> getMyInterventions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(tenantTravauxService.getMyInterventions(search, status, page, size));
    }

    // =========================================================================
    // DÉTAIL D'UNE DEMANDE DE TRAVAUX
    // =========================================================================

    @Operation(summary = "Détail d'une demande de travaux")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerInterventionDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cette demande n'appartient pas au locataire connecté",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/interventions/{interventionId}")
    public ResponseEntity<OwnerInterventionDetailDTO> getInterventionDetail(@PathVariable Long interventionId) {
        return ResponseEntity.ok(tenantTravauxService.getInterventionDetail(interventionId));
    }
}
