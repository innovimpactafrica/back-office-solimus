package com.example.solimus.controllers;


import com.example.solimus.dtos.provider.profile.ProviderProfileDTO;
import com.example.solimus.dtos.provider.profile.UpdateProviderProfileDTO;
import com.example.solimus.dtos.provider.profile.UpdateLocationDTO;
import com.example.solimus.services.provider.ProviderService;
import com.example.solimus.services.provider.profile.ProviderProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/provider/profile")
@RequiredArgsConstructor
@Tag(name = "3.a Prestataire - Profil", description = "Gestion du profil du prestataire")
public class ProviderProfileController {

    private final ProviderService providerService;
    private final ProviderProfileService providerProfileService;

    // ============================================================
    // PROFIL
    // ============================================================
    @Operation(summary = "Obtenir mon profil")
    @GetMapping
    public ResponseEntity<ProviderProfileDTO> getMyProfile() {
        return ResponseEntity.ok(providerService.getMyProfile());
    }

    @Operation(summary = "Mettre à jour mon profil")
    @PutMapping(consumes = "multipart/form-data")
    public ResponseEntity<Void> updateProfile(
            @RequestPart("dto") UpdateProviderProfileDTO dto,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        providerService.updateProfile(dto, photo);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // INFOS PERSONNELLES
    // ============================================================
    @Operation(summary = "Obtenir mes informations personnelles")
    @GetMapping("/personal-info")
    public ResponseEntity<UpdateProviderProfileDTO> getPersonalInformation() {
        return ResponseEntity.ok(providerService.getPersonalInformation());
    }

    // ============================================================
    // PARAMÈTRES DU COMPTE
    // ============================================================

    //Localisation
    @Operation(summary = "Mettre à jour ma position GPS")
    @PutMapping("/location")
    public ResponseEntity<Void> updateLocation(@Valid @RequestBody UpdateLocationDTO dto) {
        providerProfileService.updateLocation(dto);
        return ResponseEntity.noContent().build();
    }

    //Notification
    @Operation(summary = "Activer/Désactiver les notifications")
    @PutMapping("/notifications")
    public ResponseEntity<Void> toggleNotifications() {
        providerProfileService.toggleNotifications();
        return ResponseEntity.noContent().build();
    }
}
