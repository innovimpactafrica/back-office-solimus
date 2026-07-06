package com.example.solimus.controllers;

import com.example.solimus.dtos.owner.signalement.CreateSignalementDTO;
import com.example.solimus.dtos.owner.signalement.OwnerSignalementDTO;
import com.example.solimus.dtos.owner.signalement.OwnerSignalementDetailDTO;
import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.SignalementStatus;
import com.example.solimus.enums.UrgencyLevel;

import com.example.solimus.services.profile.CoOwnerProfileService;
import com.example.solimus.services.provider.profile.ProviderProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/coowner/profile")
@RequiredArgsConstructor
@Tag(name = "Copropriétaire - Profil", description = "Consultation et mise à jour du profil du copropriétaire.")
public class OwnerProfileController {

    private final CoOwnerProfileService profileService;
    private final ProviderProfileService providerProfileService;

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

    // Notification
    @Operation(summary = "Activer/Désactiver les notifications")
    @PutMapping("/notifications")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Void> toggleNotifications() {
        providerProfileService.toggleNotifications();
        return ResponseEntity.noContent().build();
    }

    // Signalement
    @Operation(summary = "Créer un signalement", description = "Permet au copropriétaire de signaler un problème")
    @PostMapping(value = "/signalements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Void> createSignalement(
            @RequestParam Long residenceId,
            @RequestParam String locationType,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long commonFacilityId,
            @RequestParam String title,
            @RequestParam String urgencyLevel,
            @RequestParam String description,
            @RequestParam(required = false) MultipartFile[] photos) {
        CreateSignalementDTO dto = CreateSignalementDTO.builder()
                .residenceId(residenceId)
                .locationType(IncidentLocationType.valueOf(locationType))
                .propertyId(propertyId)
                .commonFacilityId(commonFacilityId)
                .title(title)
                .urgencyLevel(UrgencyLevel.valueOf(urgencyLevel))
                .description(description)
                .build();

        profileService.createSignalement(dto, photos);
        return ResponseEntity.status(201).build();
    }

    @Operation(summary = "Lister mes signalements (recherche + filtres + pagination)")
    @GetMapping("/signalements")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<OwnerSignalementDTO> getMySignalements(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SignalementStatus status,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        return ResponseEntity.ok(
                profileService.getMySignalements(search, status, residenceId, page, size)
        );
    }

    @Operation(summary = "Détail d'un signalement (copropriétaire)")
    @GetMapping("/signalements/{id}")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<OwnerSignalementDetailDTO> getSignalementDetail(@PathVariable Long id) {
        return ResponseEntity.ok(profileService.getSignalementDetail(id));
    }
}
