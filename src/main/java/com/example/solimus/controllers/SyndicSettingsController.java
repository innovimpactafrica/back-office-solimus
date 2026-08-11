package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.settings.EstimatedDelayDTO;
import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.dtos.syndic.settings.*;
import com.example.solimus.dtos.syndic.subscription.InitiateSyndicPlanChangeDTO;
import com.example.solimus.dtos.syndic.subscription.MySyndicSubscriptionDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicPlanChangeResponseDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicPlanOptionDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicSubscriptionHistoryDTO;
import com.example.solimus.services.syndic.settings.SyndicSettingsService;
import com.example.solimus.services.syndic.subscription.SyndicSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/syndic/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SYNDIC')")
@Tag(name = "Syndic - Paramètres", description = "Gestion des paramètres par le syndic")
public class SyndicSettingsController {

    private final SyndicSettingsService syndicSettingsService;
    private final SyndicSubscriptionService syndicSubscriptionService;

    // ===== TYPES D'ÉQUIPEMENTS =====

    @Operation(summary = "Lister tous les types d'équipements")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = FacilityTypeDTO.class)))
    })
    @GetMapping("/facility-types")
    public ResponseEntity<Page<FacilityTypeDTO>> getAllFacilityTypes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicSettingsService.getAllFacilityTypes(page, size));
    }

    @Operation(summary = "Créer un nouveau type d'équipement")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Type d'équipement créé avec succès"),
            @ApiResponse(responseCode = "400", description = "Un type d'équipement avec ce nom existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/facility-types", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createFacilityType(
            @RequestParam("name") @NotBlank String name,
            @RequestParam("category") @NotBlank String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestPart(value = "icon", required = false) MultipartFile icon) {
        syndicSettingsService.createFacilityType(name, category, description, isActive, icon);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mettre à jour un type d'équipement")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Type d'équipement mis à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Un type d'équipement avec ce nom existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Type d'équipement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping(value = "/facility-types/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateFacilityType(
            @PathVariable Long id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestPart(value = "icon", required = false) MultipartFile icon) {
        syndicSettingsService.updateFacilityType(id, name, category, description, isActive, icon);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer un type d'équipement")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Type d'équipement supprimé avec succès"),
            @ApiResponse(responseCode = "400", description = "Des résidences utilisent encore ce type d'équipement",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Type d'équipement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/facility-types/{id}")
    public ResponseEntity<Void> deleteFacilityType(@PathVariable Long id) {
        syndicSettingsService.deleteFacilityType(id);
        return ResponseEntity.noContent().build();
    }

    // ===== SPÉCIALITÉS =====

    @Operation(summary = "Lister toutes les spécialités")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SpecialtyDTO.class)))
    })
    @GetMapping("/specialties")
    public ResponseEntity<Page<SpecialtyDTO>> getAllSpecialties(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicSettingsService.getAllSpecialties(page, size));
    }

    @Operation(summary = "Créer une spécialité")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Spécialité créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Cette spécialité existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/specialties", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createSpecialty(
            @RequestParam("name") @NotBlank String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestPart(value = "icon", required = false) MultipartFile icon) {
        syndicSettingsService.createSpecialty(name, description, icon);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mettre à jour une spécialité")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Spécialité mise à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Cette spécialité existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Spécialité introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping(value = "/specialties/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateSpecialty(
            @PathVariable Long id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestPart(value = "icon", required = false) MultipartFile icon) {
        syndicSettingsService.updateSpecialty(id, name, description, icon);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer une spécialité")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Spécialité supprimée avec succès"),
            @ApiResponse(responseCode = "404", description = "Spécialité introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/specialties/{id}")
    public ResponseEntity<Void> deleteSpecialty(@PathVariable Long id) {
        syndicSettingsService.deleteSpecialty(id);
        return ResponseEntity.noContent().build();
    }

    // ===== TYPES D'APPARTEMENT =====

    @Operation(summary = "Lister tous les types d'appartement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = PropertyTypeDTO.class)))
    })
    @GetMapping("/property-types")
    public ResponseEntity<Page<PropertyTypeDTO>> getAllPropertyTypes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicSettingsService.getAllPropertyTypes(page, size));
    }

    @Operation(summary = "Créer un type d'appartement")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Type d'appartement créé avec succès"),
            @ApiResponse(responseCode = "400", description = "Un type d'appartement avec ce nom existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/property-types")
    public ResponseEntity<Void> createPropertyType(@Valid @RequestBody CreatePropertyTypeDTO dto) {
        syndicSettingsService.createPropertyType(dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mettre à jour un type d'appartement")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Type d'appartement mis à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Un type d'appartement avec ce nom existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Type d'appartement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping("/property-types/{id}")
    public ResponseEntity<Void> updatePropertyType(
            @PathVariable Long id,
            @Valid @RequestBody CreatePropertyTypeDTO dto) {
        syndicSettingsService.updatePropertyType(id, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer un type d'appartement")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Type d'appartement supprimé avec succès"),
            @ApiResponse(responseCode = "404", description = "Type d'appartement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/property-types/{id}")
    public ResponseEntity<Void> deletePropertyType(@PathVariable Long id) {
        syndicSettingsService.deletePropertyType(id);
        return ResponseEntity.noContent().build();
    }

    // ===== OPTIONS DE SÉCURITÉ =====

    @Operation(summary = "Lister toutes les options de sécurité")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SecurityFeatureDTO.class)))
    })
    @GetMapping("/security-features")
    public ResponseEntity<Page<SecurityFeatureDTO>> getAllSecurityFeatures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicSettingsService.getAllSecurityFeatures(page, size));
    }

    @Operation(summary = "Créer une option de sécurité")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Option de sécurité créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Une option de sécurité avec ce label existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/security-features", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createSecurityFeature(
            @RequestParam("label") @NotBlank String label,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestPart(value = "icon", required = false) MultipartFile icon) {
        syndicSettingsService.createSecurityFeature(label, description, isActive, icon);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mettre à jour une option de sécurité")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Option de sécurité mise à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Une option de sécurité avec ce label existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Option de sécurité introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping(value = "/security-features/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateSecurityFeature(
            @PathVariable Long id,
            @RequestParam(value = "label", required = false) String label,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestPart(value = "icon", required = false) MultipartFile icon) {
        syndicSettingsService.updateSecurityFeature(id, label, description, isActive, icon);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer une option de sécurité")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Option de sécurité supprimée avec succès"),
            @ApiResponse(responseCode = "400", description = "Des résidences utilisent encore cette option de sécurité",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Option de sécurité introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/security-features/{id}")
    public ResponseEntity<Void> deleteSecurityFeature(@PathVariable Long id) {
        syndicSettingsService.deleteSecurityFeature(id);
        return ResponseEntity.noContent().build();
    }

    // ===== DÉLAIS ESTIMÉS =====

    @Operation(summary = "Lister tous les délais estimés")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = EstimatedDelayDTO.class)))
    })
    @GetMapping("/estimated-delays")
    public ResponseEntity<List<EstimatedDelayDTO>> getAllEstimatedDelays() {
        return ResponseEntity.ok(syndicSettingsService.getAllEstimatedDelays());
    }

    @Operation(summary = "Ajouter un nouveau délai estimé")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Délai estimé créé avec succès",
                    content = @Content(schema = @Schema(implementation = EstimatedDelayDTO.class))),
            @ApiResponse(responseCode = "400", description = "Un délai estimé avec ce label existe déjà",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/estimated-delays")
    public ResponseEntity<EstimatedDelayDTO> createEstimatedDelay(
            @RequestParam String label,
            @RequestParam Integer days) {
        return ResponseEntity.status(HttpStatus.CREATED).body(syndicSettingsService.createEstimatedDelay(label, days));
    }

    @Operation(summary = "Supprimer un délai estimé")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Délai estimé supprimé avec succès"),
            @ApiResponse(responseCode = "404", description = "Délai estimé introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/estimated-delays/{id}")
    public ResponseEntity<Void> deleteEstimatedDelay(@PathVariable Long id) {
        syndicSettingsService.deleteEstimatedDelay(id);
        return ResponseEntity.noContent().build();
    }

    // ===== PARAMÈTRES FINANCIERS =====

    @Operation(summary = "Récupérer les paramètres financiers du syndic")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paramètres renvoyés avec succès (valeurs par défaut si jamais configurés)",
                    content = @Content(schema = @Schema(implementation = SyndicFinancialSettingsDTO.class)))
    })
    @GetMapping("/financial")
    public ResponseEntity<SyndicFinancialSettingsDTO> getFinancialSettings() {
        return ResponseEntity.ok(syndicSettingsService.getFinancialSettings());
    }

    @Operation(summary = "Enregistrer les paramètres financiers du syndic")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Paramètres enregistrés avec succès")
    })
    @PutMapping("/financial")
    public ResponseEntity<Void> saveFinancialSettings(@Valid @RequestBody UpdateSyndicFinancialSettingsDTO dto) {
        syndicSettingsService.saveFinancialSettings(dto);
        return ResponseEntity.noContent().build();
    }

    // ===== PRÉFÉRENCES DE NOTIFICATIONS =====
    @Operation(summary = "Récupère les préférences de notification du syndic connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Préférences renvoyées avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicNotificationPreferencesDTO.class)))
    })
    @GetMapping("/notification-preferences")
    public ResponseEntity<SyndicNotificationPreferencesDTO> getNotificationPreferences() {
        return ResponseEntity.ok(syndicSettingsService.getNotificationPreferences());
    }

    @Operation(summary = "Met à jour les préférences de notification du syndic connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Préférences mises à jour avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicNotificationPreferencesDTO.class)))
    })
    @PatchMapping("/notification-preferences")
    public ResponseEntity<SyndicNotificationPreferencesDTO> updateNotificationPreferences(
            @RequestBody SyndicNotificationPreferencesDTO dto) {
        return ResponseEntity.ok(syndicSettingsService.updateNotificationPreferences(dto));
    }

    @Operation(summary = "Lister mes notifications (paginé)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = NotificationListResponseDTO.class)))
    })
    @GetMapping("/notifications")
    public ResponseEntity<NotificationListResponseDTO> getMyNotifications(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(syndicSettingsService.getMyNotifications(page, size));
    }

    @Operation(summary = "Marquer toutes mes notifications comme lues")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notifications marquées comme lues avec succès")
    })
    @PatchMapping("/notifications/mark-all-read")
    public ResponseEntity<Void> markAllNotificationsAsRead() {
        syndicSettingsService.markAllNotificationsAsRead();
        return ResponseEntity.noContent().build();
    }

    // ===== PROFIL SYNDIC =====

    @Operation(summary = "Récupérer le profil du syndic connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicProfileDTO.class)))
    })
    @GetMapping("/profile")
    public ResponseEntity<SyndicProfileDTO> getSyndicProfile() {
        return ResponseEntity.ok(syndicSettingsService.getSyndicProfile());
    }

    @Operation(summary = "Mettre à jour le profil du syndic connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Profil mis à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Ce numéro de téléphone est déjà utilisé par un autre utilisateur",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping(value = "/profile", consumes = "multipart/form-data")
    public ResponseEntity<Void> updateSyndicProfile(
            @RequestPart(value = "firstName", required = false) String firstName,
            @RequestPart(value = "lastName", required = false) String lastName,
            @RequestPart(value = "phone", required = false) String phone,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        syndicSettingsService.updateSyndicProfile(firstName, lastName, phone, photo);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ajouter ou remplacer la photo de profil du syndic")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Photo mise à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Aucune photo fournie",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/profile/photo", consumes = "multipart/form-data")
    public ResponseEntity<Void> updateProfilePhoto(@RequestPart("photo") MultipartFile photo) {
        syndicSettingsService.updateProfilePhoto(photo);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Changer le mot de passe du syndic connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Mot de passe changé avec succès"),
            @ApiResponse(responseCode = "400", description = "Mot de passe actuel incorrect, ou confirmation ne correspondant pas au nouveau mot de passe",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        syndicSettingsService.changePassword(dto);
        return ResponseEntity.noContent().build();
    }

    // ===== ABONNEMENT =====

    @Operation(summary = "Consulter mon abonnement actuel")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Abonnement renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = MySyndicSubscriptionDTO.class)))
    })
    @GetMapping("/subscription/me")
    public MySyndicSubscriptionDTO getMySubscription() {
        return syndicSubscriptionService.getMySubscription();
    }

    @Operation(summary = "Consulter mon historique de paiements d'abonnement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicSubscriptionHistoryDTO.class)))
    })
    @GetMapping("/subscription/history")
    public Page<SyndicSubscriptionHistoryDTO> getSubscriptionPaymentHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return syndicSubscriptionService.getPaymentHistory(page, size);
    }

    @Operation(summary = "Lister les formules disponibles pour un changement d'abonnement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicPlanOptionDTO.class)))
    })
    @GetMapping("/subscription/plans")
    public List<SyndicPlanOptionDTO> listAvailableSubscriptionPlans() {
        return syndicSubscriptionService.listAvailablePlans();
    }

    @Operation(summary = "Initier le paiement d'un changement de formule (self-service)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement initié avec succès",
                    content = @Content(schema = @Schema(implementation = SyndicPlanChangeResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Formule non disponible, ou un paiement est déjà en attente de confirmation",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Formule d'abonnement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/subscription/change-plan")
    public SyndicPlanChangeResponseDTO initiateSubscriptionPlanChange(
            @RequestBody @Valid InitiateSyndicPlanChangeDTO dto) {
        return syndicSubscriptionService.initiateChangePlan(dto);
    }
}
