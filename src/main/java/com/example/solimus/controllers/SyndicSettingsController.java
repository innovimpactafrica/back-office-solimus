package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.settings.ChangePasswordDTO;
import com.example.solimus.dtos.syndic.settings.CreateFacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreatePropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreateSpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.PropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.SecurityFeatureDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.SyndicFinancialSettingsDTO;
import com.example.solimus.dtos.syndic.settings.SyndicProfileDTO;
import com.example.solimus.dtos.syndic.settings.UpdateSyndicFinancialSettingsDTO;
import com.example.solimus.dtos.syndic.settings.UpdateSyndicProfileDTO;
import com.example.solimus.enums.FacilityCategory;
import com.example.solimus.services.syndic.settings.SyndicSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/syndic/settings")
@RequiredArgsConstructor
@Tag(name = "Syndic - Paramètres", description = "Gestion des paramètres par le syndic")
public class SyndicSettingsController {

    private final SyndicSettingsService syndicSettingsService;

    // ===== TYPES D'ÉQUIPEMENTS =====

    @Operation(summary = "Lister tous les types d'équipements")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/facility-types")
    public ResponseEntity<Page<FacilityTypeDTO>> getAllFacilityTypes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicSettingsService.getAllFacilityTypes(page, size));
    }

    @Operation(summary = "Créer un nouveau type d'équipement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
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
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
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
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/facility-types/{id}")
    public ResponseEntity<Void> deleteFacilityType(@PathVariable Long id) {
        syndicSettingsService.deleteFacilityType(id);
        return ResponseEntity.noContent().build();
    }

    // ===== SPÉCIALITÉS =====

    @Operation(summary = "Lister toutes les spécialités")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/specialties")
    public ResponseEntity<Page<SpecialtyDTO>> getAllSpecialties(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicSettingsService.getAllSpecialties(page, size));
    }

    @Operation(summary = "Créer une spécialité")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping(value = "/specialties", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createSpecialty(
            @RequestParam("name") @NotBlank String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestPart(value = "icon", required = false) MultipartFile icon) {
        syndicSettingsService.createSpecialty(name, description, icon);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mettre à jour une spécialité")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
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
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/specialties/{id}")
    public ResponseEntity<Void> deleteSpecialty(@PathVariable Long id) {
        syndicSettingsService.deleteSpecialty(id);
        return ResponseEntity.noContent().build();
    }

    // ===== TYPES D'APPARTEMENT =====

    @Operation(summary = "Lister tous les types d'appartement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/property-types")
    public ResponseEntity<Page<PropertyTypeDTO>> getAllPropertyTypes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicSettingsService.getAllPropertyTypes(page, size));
    }

    @Operation(summary = "Créer un type d'appartement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/property-types")
    public ResponseEntity<Void> createPropertyType(@Valid @RequestBody CreatePropertyTypeDTO dto) {
        syndicSettingsService.createPropertyType(dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mettre à jour un type d'appartement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/property-types/{id}")
    public ResponseEntity<Void> updatePropertyType(
            @PathVariable Long id,
            @Valid @RequestBody CreatePropertyTypeDTO dto) {
        syndicSettingsService.updatePropertyType(id, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer un type d'appartement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/property-types/{id}")
    public ResponseEntity<Void> deletePropertyType(@PathVariable Long id) {
        syndicSettingsService.deletePropertyType(id);
        return ResponseEntity.noContent().build();
    }

    // ===== OPTIONS DE SÉCURITÉ =====

    @Operation(summary = "Lister toutes les options de sécurité")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/security-features")
    public ResponseEntity<Page<SecurityFeatureDTO>> getAllSecurityFeatures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicSettingsService.getAllSecurityFeatures(page, size));
    }

    @Operation(summary = "Créer une option de sécurité")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
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
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
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
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/security-features/{id}")
    public ResponseEntity<Void> deleteSecurityFeature(@PathVariable Long id) {
        syndicSettingsService.deleteSecurityFeature(id);
        return ResponseEntity.noContent().build();
    }

    // ===== PARAMÈTRES FINANCIERS =====

    @Operation(summary = "Récupérer les paramètres financiers du syndic")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/financial")
    public ResponseEntity<SyndicFinancialSettingsDTO> getFinancialSettings() {
        return ResponseEntity.ok(syndicSettingsService.getFinancialSettings());
    }

    @Operation(summary = "Enregistrer les paramètres financiers du syndic")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/financial")
    public ResponseEntity<Void> saveFinancialSettings(@Valid @RequestBody UpdateSyndicFinancialSettingsDTO dto) {
        syndicSettingsService.saveFinancialSettings(dto);
        return ResponseEntity.noContent().build();
    }

    // ===== PROFIL SYNDIC =====

    @Operation(summary = "Récupérer le profil du syndic connecté")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/profile")
    public ResponseEntity<SyndicProfileDTO> getSyndicProfile() {
        return ResponseEntity.ok(syndicSettingsService.getSyndicProfile());
    }

    @Operation(summary = "Mettre à jour le profil du syndic connecté")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
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
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping(value = "/profile/photo", consumes = "multipart/form-data")
    public ResponseEntity<Void> updateProfilePhoto(@RequestPart("photo") MultipartFile photo) {
        syndicSettingsService.updateProfilePhoto(photo);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Changer le mot de passe du syndic connecté")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        syndicSettingsService.changePassword(dto);
        return ResponseEntity.noContent().build();
    }
}
