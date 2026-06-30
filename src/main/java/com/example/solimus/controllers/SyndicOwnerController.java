package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.owner.CreateCoOwnerDTO;
import com.example.solimus.dtos.syndic.owner.PropertySummaryDTO;
import com.example.solimus.dtos.syndic.owner.ResidenceSummaryDTO;
import com.example.solimus.dtos.syndic.owner.CoOwnerListDTO;
import com.example.solimus.dtos.syndic.owner.CoOwnerSearchResultDTO;
import com.example.solimus.services.syndic.owner.SyndicOwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "Syndic - Copropriétaires", description = "Gestion des copropriétaires par le syndic")
public class SyndicOwnerController {

    private final SyndicOwnerService syndicOwnerService;

    @Operation(summary = "Ajouter un copropriétaire (Workflow OTP)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/co-owners")
    public ResponseEntity<String> addCoOwner(@RequestBody @Valid CreateCoOwnerDTO dto) {
        syndicOwnerService.addCoOwner(dto);
        return ResponseEntity.ok("Copropriétaire ajouté avec succès. Un code d'activation lui a été envoyé par email.");
    }

    @Operation(summary = "Lister les biens disponibles (VACANT) d'une résidence", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/properties/available")
    public ResponseEntity<List<PropertySummaryDTO>> getAvailableProperties(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(syndicOwnerService.getAvailableProperties(residenceId));
    }

    @Operation(summary = "Lister les résidences qui ont au moins un bien vacant", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/with-vacant-properties")
    public ResponseEntity<List<ResidenceSummaryDTO>> getResidencesWithVacantProperties() {
        return ResponseEntity.ok(syndicOwnerService.getResidencesWithVacantProperties());
    }

    @Operation(summary = "Lister les copropriétaires (recherche + filtre résidence + pagination)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners")
    public ResponseEntity<Page<CoOwnerListDTO>> getCoOwners(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("firstName").ascending());
        return ResponseEntity.ok(syndicOwnerService.getCoOwners(search, residenceId, pageable));
    }

    @Operation(summary = "Autocomplete — rechercher un copropriétaire par nom, email ou téléphone", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/search")
    public ResponseEntity<List<CoOwnerSearchResultDTO>> searchCoOwners(
            @RequestParam String q) {
        return ResponseEntity.ok(syndicOwnerService.searchCoOwners(q));
    }

    @Operation(summary = "Lier un copropriétaire existant au syndic connecté", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/co-owners/{id}/link")
    public ResponseEntity<String> linkCoOwner(@PathVariable Long id) {
        syndicOwnerService.linkCoOwner(id);
        return ResponseEntity.ok("Copropriétaire lié avec succès");
    }
}
