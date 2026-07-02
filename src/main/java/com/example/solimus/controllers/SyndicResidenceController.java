package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.residence.*;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.services.syndic.residence.SyndicResidenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "Syndic - Résidences", description = "Gestion des résidences par le syndic")
public class SyndicResidenceController {

    private final SyndicResidenceService residenceService;
    private final ObjectMapper objectMapper;

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
    @PostMapping(value = "/residences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResidenceDTO> createResidence(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "photo", required = true) MultipartFile photo) throws JsonProcessingException {

        CreateResidenceDTO dto = objectMapper.readValue(dataJson, CreateResidenceDTO.class);
        ResidenceDTO result = residenceService.createResidenceComplete(dto, photo);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
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

    @Operation(summary = "Modifier un lot/appartement (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/residences/{id}/properties/{propertyId}")
    public ResponseEntity<PropertyDTO> updateProperty(
            @PathVariable Long id,
            @PathVariable Long propertyId,
            @RequestBody @Valid UpdatePropertyDTO dto) {
        return ResponseEntity.ok(residenceService.updateProperty(id, propertyId, dto));
    }

    @Operation(summary = "Supprimer un lot/appartement (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/residences/{id}/properties/{propertyId}")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable Long id,
            @PathVariable Long propertyId) {
        residenceService.deleteProperty(id, propertyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lister les lots d'une résidence (paginé) (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{id}/properties")
    public ResponseEntity<Page<PropertyListDTO>> getPropertiesPaginated(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "5") Integer size) {
        return ResponseEntity.ok(residenceService.getPropertiesPaginated(id, page, size));
    }

    @Operation(summary = "Lister les copropriétaires pour affecter un lot (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/properties/co-owners")
    public ResponseEntity<List<CoOwnerSelectionDTO>> searchCoOwnersForSelection(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(residenceService.searchCoOwnersForSelection(search));
    }

    @Operation(summary = "Lister tous les types de biens (dropdown) (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/property-types")
    public ResponseEntity<List<PropertyTypeDTO>> getAllPropertyTypes() {
        return ResponseEntity.ok(residenceService.getAllPropertyTypes());
    }

    // =========================================================================
    // ÉTAPE 3 — BIENS COMMUNS
    // =========================================================================
    @Operation(summary = "Lister les types d'équipements avec leurs champs (Étape 3)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/facility-types")
    public ResponseEntity<List<FacilityTypeDTO>> getFacilityTypes() {
        return ResponseEntity.ok(residenceService.getFacilityTypes());
    }

    @Operation(summary = "Ajouter un équipement commun (Étape 3)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/residences/{id}/facilities")
    public ResponseEntity<Void> addFacility(
            @PathVariable Long id,
            @RequestBody @Valid AddFacilityDTO dto) {
        residenceService.addFacility(id, dto);
        return ResponseEntity.ok().build();
    }


}
