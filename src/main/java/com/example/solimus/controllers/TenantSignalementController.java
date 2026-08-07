package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.owner.signalement.CreateSignalementDTO;
import com.example.solimus.dtos.owner.signalement.SignalementCardDTO;
import com.example.solimus.dtos.owner.signalement.SignalementDetailDTO;
import com.example.solimus.dtos.tenant.TenantPropertyInfoDTO;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.tenant.signalement.TenantSignalementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_LOCATAIRE')")
@Tag(name = "Locataire - Signalements", description = "Signalements du locataire sur son bien loué")
public class TenantSignalementController {

    private final TenantSignalementService tenantSignalementService;
    private final MinioService minioService;

    // =========================================================================
    // MON BIEN LOUÉ
    // =========================================================================

    @Operation(summary = "Voir mon bien loué", description = "Retourne la référence du lot et le nom de la résidence du locataire connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bien renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = TenantPropertyInfoDTO.class))),
            @ApiResponse(responseCode = "403", description = "Aucun bien n'est assigné à ce compte locataire",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/my-property")
    public ResponseEntity<TenantPropertyInfoDTO> getMyProperty() {
        return ResponseEntity.ok(tenantSignalementService.getMyProperty());
    }

    // =========================================================================
    // CRÉER UN SIGNALEMENT
    // =========================================================================

    @Operation(summary = "Créer un signalement", description = "Permet au locataire de signaler un incident sur son bien loué ou une partie commune, avec photos optionnelles")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Signalement créé avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides (ex: commonFacilityId manquant ou n'appartenant pas à la résidence)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Aucun bien n'est assigné à ce compte locataire",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/signalements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createSignalement(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam IncidentLocationType locationType,
            @RequestParam UrgencyLevel urgencyLevel,
            @RequestParam(required = false) Long commonFacilityId,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {

        // Upload chaque photo vers MinIO et récupère leurs URLs
        List<String> photoUrls = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile photo : photos) {
                String url = minioService.uploadFile(photo, "signalements");
                photoUrls.add(url);
            }
        }

        // Le bien concerné (propertyId/residenceId) est déterminé côté service à partir du locataire connecté
        CreateSignalementDTO dto = CreateSignalementDTO.builder()
                .title(title)
                .description(description)
                .locationType(locationType)
                .urgencyLevel(urgencyLevel)
                .commonFacilityId(commonFacilityId)
                .photoUrls(photoUrls)
                .build();

        tenantSignalementService.createSignalement(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // =========================================================================
    // LISTER MES SIGNALEMENTS
    // =========================================================================

    @Operation(summary = "Lister mes signalements", description = "Retourne la liste paginée des signalements du locataire connecté, avec recherche et filtre par statut")
    @GetMapping("/signalements")
    public ResponseEntity<Page<SignalementCardDTO>> getMySignalements(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SignalementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(tenantSignalementService.getMySignalements(search, status, page, size));
    }

    // =========================================================================
    // DÉTAIL D'UN SIGNALEMENT
    // =========================================================================

    @Operation(summary = "Détail d'un signalement", description = "Retourne le détail complet d'un signalement du locataire connecté, avec historique")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SignalementDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Ce signalement n'appartient pas au locataire connecté",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Signalement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/signalements/{id}")
    public ResponseEntity<SignalementDetailDTO> getSignalementDetail(@PathVariable Long id) {
        return ResponseEntity.ok(tenantSignalementService.getSignalementDetail(id));
    }
}
