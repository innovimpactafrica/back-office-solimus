package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.owner.travaux.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.services.owner.travaux.ownerTraveauxService;
import com.example.solimus.services.syndic.settings.SyndicParametreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/travaux")
@RequiredArgsConstructor
@Tag(name = "7. Copropriétaire - Travaux", description = "Gestion des demandes de travaux du copropriétaire")
public class OwnerTravauxController {

    private final SyndicParametreService syndicParametreService;
    private final ownerTraveauxService ownerTraveauxService;

    @Operation(summary = "Lister toutes les spécialités disponibles")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyDTO>> getAllSpecialties() {
        return ResponseEntity.ok(syndicParametreService.getAllSpecialties());
    }

    @Operation(summary = "Lister mes résidences")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    @GetMapping("/residences")
    public ResponseEntity<List<ResidenceDTO>> getMyResidences() {
        return ResponseEntity.ok(ownerTraveauxService.getMyResidences());
    }

    @Operation(summary = "Lister les parties communes d'une résidence")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    @GetMapping("/residences/{residenceId}/common-facilities")
    public ResponseEntity<List<CommonFacilityDTO>> getCommonFacilitiesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(ownerTraveauxService.getCommonFacilitiesByResidence(residenceId));
    }

    @Operation(summary = "Lister mes biens dans une résidence")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    @GetMapping("/residences/{residenceId}/properties")
    public ResponseEntity<List<PropertyDTO>> getMyPropertiesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(ownerTraveauxService.getMyPropertiesByResidence(residenceId));
    }

    @Operation(summary = "Créer une demande d'intervention")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    @PostMapping("/interventions")
    public ResponseEntity<Void> createIntervention(@Valid @RequestBody CreateOwnerInterventionRequestDTO dto) {
        ownerTraveauxService.createIntervention(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Lister mes demandes de travaux (recherche + filtres + pagination)")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    @GetMapping("/interventions")
    public ResponseEntity<OwnerInterventionDTO> getMyInterventions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ownerTraveauxService.getMyInterventions(search, status, residenceId, page, size));
    }

    @Operation(summary = "Détail d'une intervention")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    @GetMapping("/interventions/{interventionId}")
    public ResponseEntity<OwnerInterventionDetailDTO> getInterventionDetail(
            @Parameter(description = "ID de l'intervention")
            @PathVariable Long interventionId) {
        return ResponseEntity.ok(ownerTraveauxService.getInterventionDetail(interventionId));
    }

    @Operation(summary = "Créer un avis pour une intervention terminée")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    @PostMapping("/interventions/{interventionId}/review")
    public ResponseEntity<String> createReview(
            @PathVariable Long interventionId,
            @RequestBody @Valid CreateReviewDTO dto) {
        ownerTraveauxService.createReview(interventionId, dto);
        return ResponseEntity.ok("Avis créé avec succès");
    }
}
