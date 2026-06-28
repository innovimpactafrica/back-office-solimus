package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.settings.CreateFacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreatePropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreateSpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.PropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.services.syndic.settings.SyndicParametreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/syndic/parametres")
@RequiredArgsConstructor
@Tag(name = "4.b Syndic - Paramètres", description = "Gestion des paramètres par le syndic")
public class SyndicParametreController {

    private final SyndicParametreService syndicParametreService;

    // ===== TYPES D'ÉQUIPEMENTS =====

    @Operation(summary = "Lister tous les types d'équipements")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/facility-types")
    public ResponseEntity<List<FacilityTypeDTO>> getAllFacilityTypes() {
        return ResponseEntity.ok(syndicParametreService.getAllFacilityTypes());
    }

    @Operation(summary = "Créer un nouveau type d'équipement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/facility-types")
    public ResponseEntity<Void> createFacilityType(@Valid @RequestBody CreateFacilityTypeDTO dto) {
        syndicParametreService.createFacilityType(dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mettre à jour un type d'équipement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/facility-types/{id}")
    public ResponseEntity<Void> updateFacilityType(
            @PathVariable Long id,
            @Valid @RequestBody CreateFacilityTypeDTO dto) {
        syndicParametreService.updateFacilityType(id, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer un type d'équipement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/facility-types/{id}")
    public ResponseEntity<Void> deleteFacilityType(@PathVariable Long id) {
        syndicParametreService.deleteFacilityType(id);
        return ResponseEntity.noContent().build();
    }

    // ===== SPÉCIALITÉS =====

    @Operation(summary = "Lister toutes les spécialités")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyDTO>> getAllSpecialties() {
        return ResponseEntity.ok(syndicParametreService.getAllSpecialties());
    }

    @Operation(summary = "Créer une spécialité")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/specialties")
    public ResponseEntity<Void> createSpecialty(@Valid @RequestBody CreateSpecialtyDTO dto) {
        syndicParametreService.createSpecialty(dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mettre à jour une spécialité")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/specialties/{id}")
    public ResponseEntity<Void> updateSpecialty(
            @PathVariable Long id,
            @Valid @RequestBody CreateSpecialtyDTO dto) {
        syndicParametreService.updateSpecialty(id, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer une spécialité")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/specialties/{id}")
    public ResponseEntity<Void> deleteSpecialty(@PathVariable Long id) {
        syndicParametreService.deleteSpecialty(id);
        return ResponseEntity.noContent().build();
    }

    // ===== TYPES D'APPARTEMENT =====

    @Operation(summary = "Lister tous les types d'appartement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/property-types")
    public ResponseEntity<List<PropertyTypeDTO>> getAllPropertyTypes() {
        return ResponseEntity.ok(syndicParametreService.getAllPropertyTypes());
    }

    @Operation(summary = "Créer un type d'appartement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/property-types")
    public ResponseEntity<Void> createPropertyType(@Valid @RequestBody CreatePropertyTypeDTO dto) {
        syndicParametreService.createPropertyType(dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mettre à jour un type d'appartement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/property-types/{id}")
    public ResponseEntity<Void> updatePropertyType(
            @PathVariable Long id,
            @Valid @RequestBody CreatePropertyTypeDTO dto) {
        syndicParametreService.updatePropertyType(id, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer un type d'appartement")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/property-types/{id}")
    public ResponseEntity<Void> deletePropertyType(@PathVariable Long id) {
        syndicParametreService.deletePropertyType(id);
        return ResponseEntity.noContent().build();
    }
}
