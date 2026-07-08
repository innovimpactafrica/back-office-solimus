package com.example.solimus.controllers;

import com.example.solimus.dtos.owner.signalement.CreateSignalementDTO;
import com.example.solimus.dtos.owner.signalement.SignalementCardDTO;
import com.example.solimus.dtos.owner.signalement.SignalementDetailDTO;
import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.owner.profile.ProfileService;
import com.example.solimus.services.owner.signalement.OwnerSignalementService;
import com.example.solimus.services.provider.profile.ProviderProfileService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/coowner/profile")
@RequiredArgsConstructor
@Tag(name = "Copropriétaire - Profil", description = "Consultation et mise à jour du profil du copropriétaire.")
public class OwnerProfileController {

    private final ProfileService profileService;
    private final ProviderProfileService providerProfileService;
    private final OwnerSignalementService signalementService;
    private final MinioService minioService;

    // =========================================================================
    // PROFIL
    // =========================================================================

    @Operation(summary = "Voir mon profil")
    @GetMapping
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<CoOwnerProfileDTO> getProfile() {
        return ResponseEntity.ok(profileService.getProfile());
    }

    @Operation(summary = "Modifier mon profil", description = "Permet de modifier : prénom, nom, téléphone et photo de profil. L'email et le bien sont non modifiables.")
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<CoOwnerProfileDTO> updateProfile(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) MultipartFile photo) {
        UpdateCoOwnerProfileDTO dto = UpdateCoOwnerProfileDTO.builder()
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .build();

        return ResponseEntity.ok(profileService.updateProfile(dto, photo));
    }

    // =========================================================================
    // NOTIFICATIONS
    // =========================================================================

    @Operation(summary = "Activer/Désactiver les notifications")
    @PutMapping("/notifications")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Void> toggleNotifications() {
        providerProfileService.toggleNotifications();
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // SIGNALEMENTS — CRÉER UN SIGNALEMENT
    // =========================================================================

    @Operation(summary = "Créer un signalement", description = "Permet au copropriétaire de signaler un incident, avec photos optionnelles")
    @PostMapping(value = "/signalements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Void> createSignalement(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam Long residenceId,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long commonFacilityId,
            @RequestParam IncidentLocationType locationType,
            @RequestParam UrgencyLevel urgencyLevel,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {

        // Upload chaque photo vers MinIO et récupère leurs URLs
        List<String> photoUrls = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile photo : photos) {
                String url = minioService.uploadFile(photo, "signalements");
                photoUrls.add(url);
            }
        }

        // Construit le DTO à partir des paramètres reçus, pour la couche service
        CreateSignalementDTO dto = CreateSignalementDTO.builder()
                .title(title)
                .description(description)
                .residenceId(residenceId)
                .propertyId(propertyId)
                .commonFacilityId(commonFacilityId)
                .locationType(locationType)
                .urgencyLevel(urgencyLevel)
                .photoUrls(photoUrls)
                .build();

        signalementService.createSignalement(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // =========================================================================
    // SIGNALEMENTS — LISTER MES SIGNALEMENTS
    // =========================================================================

    @Operation(summary = "Lister mes signalements", description = "Retourne la liste paginée des signalements du copropriétaire connecté, avec recherche et filtres")
    @GetMapping("/signalements")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Page<SignalementCardDTO>> getMySignalements(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(required = false) SignalementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(signalementService.getMySignalements(search, residenceId, status, page, size));
    }

    // =========================================================================
    // SIGNALEMENTS — DÉTAIL D'UN SIGNALEMENT
    // =========================================================================

    @Operation(summary = "Détail d'un signalement", description = "Retourne le détail complet d'un signalement du copropriétaire connecté, avec historique")
    @GetMapping("/signalements/{id}")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<SignalementDetailDTO> getSignalementDetail(@PathVariable Long id) {
        return ResponseEntity.ok(signalementService.getSignalementDetail(id));
    }
}