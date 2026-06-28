package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.travaux.CreateInterventionRequestDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.syndic.SyndicService;
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
@Tag(name = "4.c Syndic - Travaux", description = "Gestion des travaux par le syndic")
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
    @Operation(summary = "Créer une demande de travaux (Avec upload Minio)", tags = {"4.c Syndic - Travaux"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping(value = "/interventions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createIntervention(
            @Parameter(description = "Titre court de l'intervention (ex: Fuite d'eau)")
            @RequestPart("title") String title,

            @Parameter(description = "Description détaillée du problème")
            @RequestPart("description") String description,

            @Parameter(description = "ID de la résidence concernée")
            @RequestPart("residenceId") Long residenceId,

            @Parameter(description = "ID du bien concerné (obligatoire si APPARTEMENT)")
            @RequestPart(value = "propertyId", required = false) Long propertyId,

            @Parameter(description = "ID de la partie commune concernée (obligatoire si PARTIE_COMMUNE)")
            @RequestPart(value = "commonFacilityId", required = false) Long commonFacilityId,

            @Parameter(description = "ID de la spécialité requise (Plomberie, Électricité, etc.)")
            @RequestPart("specialtyId") Long specialtyId,

            @Parameter(description = "Type de localisation (APPARTEMENT ou PARTIE_COMMUNE)")
            @RequestPart("locationType") IncidentLocationType locationType,

            @Parameter(description = "Niveau d'urgence (LOW, MEDIUM, HIGH)")
            @RequestPart("urgencyLevel") UrgencyLevel urgencyLevel,

            @Parameter(description = "Photos du problème (JPG, PNG uniquement)")
            @RequestPart(value = "photos", required = false) MultipartFile[] photos) {

        try {
            // Liste pour stocker les noms des photos uploadées
            List<String> photoNames = new ArrayList<>();

            // Vérification et traitement des photos si présentes
            if (photos != null && photos.length > 0) {
                for (MultipartFile photo : photos) {
                    if (photo.isEmpty()) continue; // Ignorer les fichiers vides

                    // Récupération du nom original du fichier
                    String originalFilename = photo.getOriginalFilename();
                    if (originalFilename != null) {
                        // Extraction de l'extension du fichier
                        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                        // Vérification du format (JPG, JPEG, PNG uniquement)
                        if (!extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png")) {
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                    .body("Format d'image non supporté. Formats acceptés: JPG, PNG, JPEG");
                        }
                    }

                    // Upload du fichier sur Minio dans le dossier "interventions"
                    String uploadedFileName = minioService.uploadFile(photo, "interventions");
                    if (uploadedFileName != null) {
                        photoNames.add(uploadedFileName); // Ajout du nom du fichier uploadé à la liste
                    }
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
            dto.setPhotoUrls(photoNames); // Ajout des noms des photos uploadées

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
}
