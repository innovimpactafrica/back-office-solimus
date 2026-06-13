package com.example.solimus.controllers;

import com.example.solimus.dtos.provider.ProviderProfileDTO;
import com.example.solimus.dtos.provider.UpdateProviderProfileDTO;
import com.example.solimus.services.provider.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/provider/profil")
@RequiredArgsConstructor
@Tag(name = "5.d Prestataire - Profil", description = "Informations personnelles, abonnement et notifications")
public class ProviderProfilController {

    private final ProviderService providerService;

    // ==================== INFORMATIONS PERSONNELLES ====================

    @Operation(summary = "Récupérer le profil du prestataire connecté")
    @GetMapping
    public ResponseEntity<ProviderProfileDTO> getMyProfile() {
        return ResponseEntity.ok(providerService.getMyProfile());
    }

    @Operation(summary = "Activer ou désactiver la disponibilité (En ligne / Hors ligne)")
    @PostMapping("/toggle-availability")
    public ResponseEntity<String> toggleAvailability() {
        providerService.toggleAvailability();
        return ResponseEntity.ok("Statut de disponibilité mis à jour avec succès.");
    }

    @Operation(summary = "Récupérer les informations personnelles (Édition profil)")
    @GetMapping("/personal-info")
    public ResponseEntity<UpdateProviderProfileDTO> getPersonalInformation() {
        return ResponseEntity.ok(providerService.getPersonalInformation());
    }

    @Operation(summary = "Mettre à jour les informations personnelles du profil")
    @PutMapping(value = "/personal-info", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> updateProfile(
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "firstName", required = false) String firstName,
            @RequestParam(value = "lastName", required = false) String lastName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "interventionZone", required = false) String interventionZone,
            @RequestParam(value = "latitude", required = false) java.math.BigDecimal latitude,
            @RequestParam(value = "longitude", required = false) java.math.BigDecimal longitude,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {

        UpdateProviderProfileDTO dto =
                UpdateProviderProfileDTO.builder()
                        .companyName(companyName)
                        .firstName(firstName)
                        .lastName(lastName)
                        .phone(phone)
                        .email(email)
                        .interventionZone(interventionZone)
                        .latitude(latitude)
                        .longitude(longitude)
                        .build();

        providerService.updateProfile(dto, photo);
        return ResponseEntity.ok("Profil mis à jour avec succès.");
    }

    // ==================== ABONNEMENT ====================

    @Operation(summary = "Récupérer les détails de l'abonnement actuel")
    @GetMapping("/subscription")
    public ResponseEntity<com.example.solimus.dtos.subscription.SubscriptionDTO> getSubscription() {
        return ResponseEntity.ok(providerService.getMonAbonnement());
    }

    @Operation(summary = "Passer au plan Premium")
    @PostMapping("/subscription/premium")
    public ResponseEntity<com.example.solimus.dtos.syndic.PaymentResponseDTO> passerEnPremium(
            @RequestBody @Valid com.example.solimus.dtos.subscription.SouscrirePremiumDTO dto) {
        return ResponseEntity.ok(providerService.passerEnPremium(dto));
    }

    @Operation(summary = "Annuler le renouvellement automatique")
    @PostMapping("/subscription/cancel")
    public ResponseEntity<String> annulerAbonnement() {
        providerService.annulerAbonnement();
        return ResponseEntity.ok("Renouvellement automatique désactivé.");
    }

    // ==================== NOTIFICATIONS ====================

    @Operation(summary = "Activer ou désactiver les notifications")
    @PostMapping("/notifications/toggle")
    public ResponseEntity<String> toggleNotifications() {
        providerService.toggleNotifications();
        return ResponseEntity.ok("Préférences de notification mises à jour.");
    }

    // ==================== PARAMÈTRES DU COMPTE ====================

    @Operation(summary = "Changer le mot de passe")
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword) {
        providerService.changePassword(currentPassword, newPassword);
        return ResponseEntity.ok("Mot de passe changé avec succès.");
    }

    @Operation(summary = "Supprimer le compte")
    @DeleteMapping("/delete-account")
    public ResponseEntity<String> deleteAccount() {
        providerService.deleteAccount();
        return ResponseEntity.ok("Compte supprimé avec succès.");
    }
}
