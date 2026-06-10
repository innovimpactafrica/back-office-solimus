package com.example.solimus.controllers;

import com.example.solimus.dtos.residence.PropertyDTO;
import com.example.solimus.dtos.residence.*;
import com.example.solimus.services.syndic.SyndicResidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "4.a Syndic - Résidences", description = "Gestion des résidences par le syndic")
public class SyndicResidenceController {

    private final SyndicResidenceService residenceService;

    // =========================================================================
    // GÉNÉRAL
    // =========================================================================

    @Operation(summary = "Lister mes résidences", tags = {"4.a Syndic - Résidences"})
    @GetMapping("/residences")
    public ResponseEntity<List<ResidenceDTO>> getMesResidences() {
        return ResponseEntity.ok(residenceService.getMesResidences());
    }

    @Operation(summary = "Détail complet d'une résidence", tags = {"4.a Syndic - Résidences"})
    @GetMapping("/residences/{id}")
    public ResponseEntity<ResidenceDTO> getResidenceDetail(@PathVariable Long id) {
        return ResponseEntity.ok(residenceService.getResidenceDetail(id));
    }

    // =========================================================================
    // ÉTAPE 1 — RÉSIDENCE
    // =========================================================================

    @Operation(summary = "Créer une nouvelle résidence (Étape 1)", tags = {"4.a Syndic - Résidences"})
    @PostMapping("/residences")
    public ResponseEntity<ResidenceDTO> createResidence(@RequestBody @Valid CreateResidenceDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residenceService.createResidence(dto));
    }

    @Operation(summary = "Uploader la photo principale (Étape 1)", tags = {"4.a Syndic - Résidences"})
    @PostMapping("/residences/{id}/photo")
    public ResponseEntity<ResidenceDTO> uploadPhoto(
            @PathVariable Long id,
            @RequestParam("photo") MultipartFile photo) {
        return ResponseEntity.ok(residenceService.uploadPhoto(id, photo));
    }

    @Operation(summary = "Ajouter un contact clé (Étape 1)", tags = {"4.a Syndic - Résidences"})
    @PostMapping("/residences/{id}/contacts")
    public ResponseEntity<AddResidenceContactDTO> addContact(
            @PathVariable Long id,
            @RequestBody @Valid AddResidenceContactDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residenceService.addContact(id, dto));
    }

    // =========================================================================
    // ÉTAPE 2 — LOTS
    // =========================================================================

    @Operation(summary = "Ajouter un lot/appartement (Étape 2)", tags = {"4.a Syndic - Résidences"})
    @PostMapping("/residences/{id}/properties")
    public ResponseEntity<PropertyDTO> addProperty(
            @PathVariable Long id,
            @RequestBody @Valid AddPropertyDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residenceService.addProperty(id, dto));
    }

    @Operation(summary = "Lister les lots d'une résidence (Étape 2)", tags = {"4.a Syndic - Résidences"})
    @GetMapping("/residences/{id}/properties")
    public ResponseEntity<List<PropertyListDTO>> getPropertiesByResidence(@PathVariable Long id) {
        return ResponseEntity.ok(residenceService.getPropertiesByResidence(id));
    }

    @Operation(summary = "Compter les lots d'une résidence (Étape 2)", tags = {"4.a Syndic - Résidences"})
    @GetMapping("/residences/{id}/properties/count")
    public ResponseEntity<Long> countPropertiesByResidence(@PathVariable Long id) {
        return ResponseEntity.ok(residenceService.countPropertiesByResidence(id));
    }

    @Operation(summary = "Modifier un lot d'une résidence (Étape 2)", tags = {"4.a Syndic - Résidences"})
    @PutMapping("/residences/{id}/properties/{propertyId}")
    public ResponseEntity<PropertyListDTO> updateProperty(
            @PathVariable Long id,
            @PathVariable Long propertyId,
            @RequestBody @Valid UpdatePropertyDTO dto) {
        return ResponseEntity.ok(residenceService.updateProperty(id, propertyId, dto));
    }

    @Operation(summary = "Supprimer un lot d'une résidence (Étape 2)", tags = {"4.a Syndic - Résidences"})
    @DeleteMapping("/residences/{id}/properties/{propertyId}")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable Long id,
            @PathVariable Long propertyId) {
        residenceService.deleteProperty(id, propertyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lister les copropriétaires pour affecter un lot (Étape 2)", tags = {"4.a Syndic - Résidences"})
    @GetMapping("/residences/properties/co-owners")
    public ResponseEntity<List<CoOwnerSelectionDTO>> searchCoOwnersForSelection(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(residenceService.searchCoOwnersForSelection(search));
    }

    @Operation(summary = "Affecter un copropriétaire à un lot (Étape 2)", tags = {"4.a Syndic - Résidences"})
    @PutMapping("/residences/{id}/properties/{propertyId}/owner/{ownerId}")
    public ResponseEntity<PropertyListDTO> assignOwnerToProperty(
            @PathVariable Long id,
            @PathVariable Long propertyId,
            @PathVariable Long ownerId) {
        return ResponseEntity.ok(residenceService.assignOwnerToProperty(id, propertyId, ownerId));
    }

    @Operation(summary = "Retirer un copropriétaire d'un lot (Étape 2)", tags = {"4.a Syndic - Résidences"})
    @DeleteMapping("/residences/{id}/properties/{propertyId}/owner")
    public ResponseEntity<PropertyListDTO> removeOwnerFromProperty(
            @PathVariable Long id,
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(residenceService.removeOwnerFromProperty(id, propertyId));
    }

    // =========================================================================
    // ÉTAPE 3 — ÉQUIPEMENTS & SÉCURITÉ
    // =========================================================================

    @Operation(summary = "Ajouter un équipement commun (Étape 3)", tags = {"4.a Syndic - Résidences"})
    @PostMapping("/residences/{id}/facilities")
    public ResponseEntity<AddFacilityDTO> addFacility(
            @PathVariable Long id,
            @RequestBody @Valid AddFacilityDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residenceService.addFacility(id, dto));
    }

    @Operation(summary = "Lister les options de sécurité actives (Étape 3)", tags = {"4.a Syndic - Résidences"})
    @GetMapping("/security-features")
    public ResponseEntity<List<SecurityFeatureSimpleDTO>> getActiveSecurityFeatures() {
        return ResponseEntity.ok(residenceService.getActiveSecurityFeatures());
    }

    @Operation(summary = "Ajouter les options de sécurité à une résidence (Étape 3)", tags = {"4.a Syndic - Résidences"})
    @PostMapping("/residences/{id}/security-features")
    public ResponseEntity<Void> addSecurityFeatures(
            @PathVariable Long id,
            @RequestBody @Valid AddSecurityFeaturesDTO dto) {
        residenceService.addSecurityFeatures(id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
