package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.residence.*;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.services.syndic.residence.SyndicResidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "Syndic - Résidences", description = "Gestion des résidences par le syndic")
public class SyndicResidenceController {

    private final SyndicResidenceService residenceService;

    // =========================================================================
    // GÉNÉRAL
    // =========================================================================

    @Operation(summary = "Lister mes résidences", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences")
    public ResponseEntity<List<ResidenceDTO>> getMesResidences() {
        return ResponseEntity.ok(residenceService.getMesResidences());
    }

    @Operation(summary = "Détail complet d'une résidence", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{id}")
    public ResponseEntity<ResidenceDTO> getResidenceDetail(@PathVariable Long id) {
        return ResponseEntity.ok(residenceService.getResidenceDetail(id));
    }

    // =========================================================================
    // ÉTAPE 1 — RÉSIDENCE
    // =========================================================================

    @Operation(summary = "Créer une nouvelle résidence (Étape 1)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/residences")
    public ResponseEntity<ResidenceDTO> createResidence(@RequestBody @Valid CreateResidenceDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residenceService.createResidence(dto));
    }

    @Operation(summary = "Uploader la photo principale (Étape 1)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/residences/{id}/photo")
    public ResponseEntity<ResidenceDTO> uploadPhoto(
            @PathVariable Long id,
            @RequestParam("photo") MultipartFile photo) {
        return ResponseEntity.ok(residenceService.uploadPhoto(id, photo));
    }

    @Operation(summary = "Ajouter un contact clé (Étape 1)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/residences/{id}/contacts")
    public ResponseEntity<AddResidenceContactDTO> addContact(
            @PathVariable Long id,
            @RequestBody @Valid AddResidenceContactDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residenceService.addContact(id, dto));
    }

    // =========================================================================
    // ÉTAPE 2 — LOTS
    // =========================================================================

    @Operation(summary = "Ajouter un lot/appartement (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/residences/{id}/properties")
    public ResponseEntity<PropertyDTO> addProperty(
            @PathVariable Long id,
            @RequestBody @Valid AddPropertyDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residenceService.addProperty(id, dto));
    }

    @Operation(summary = "Lister les copropriétaires pour affecter un lot (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/properties/co-owners")
    public ResponseEntity<List<CoOwnerSelectionDTO>> searchCoOwnersForSelection(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(residenceService.searchCoOwnersForSelection(search));
    }

    @Operation(summary = "Affecter un copropriétaire à un lot (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/residences/{id}/properties/{propertyId}/owner/{ownerId}")
    public ResponseEntity<PropertyListDTO> assignOwnerToProperty(
            @PathVariable Long id,
            @PathVariable Long propertyId,
            @PathVariable Long ownerId) {
        return ResponseEntity.ok(residenceService.assignOwnerToProperty(id, propertyId, ownerId));
    }

    @Operation(summary = "Lister tous les types de biens (dropdown)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/property-types")
    public ResponseEntity<List<PropertyTypeDTO>> getAllPropertyTypes() {
        return ResponseEntity.ok(residenceService.getAllPropertyTypes());
    }

    // =========================================================================
    // ÉTAPE 3 — BIENS COMMUNS
    // =========================================================================
    @Operation(summary = "Lister les types d'équipements avec leurs champs", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/facility-types")
    public ResponseEntity<List<FacilityTypeDTO>> getFacilityTypes() {
        return ResponseEntity.ok(residenceService.getFacilityTypes());
    }

    @Operation(summary = "Ajouter un équipement commun", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/residences/{id}/facilities")
    public ResponseEntity<Void> addFacility(
            @PathVariable Long id,
            @RequestBody @Valid AddFacilityDTO dto) {
        residenceService.addFacility(id, dto);
        return ResponseEntity.ok().build();
    }


}
