package com.example.solimus.controllers;

import com.example.solimus.dtos.owner.CreateCoOwnerDTO;
import com.example.solimus.dtos.owner.PropertySummaryDTO;
import com.example.solimus.dtos.owner.ResidenceSummaryDTO;
import com.example.solimus.services.syndic.SyndicOwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "4.d Syndic - Copropriétaires", description = "Gestion des copropriétaires par le syndic")
public class SyndicOwnerController {

    private final SyndicOwnerService syndicOwnerService;

    @Operation(summary = "Ajouter un copropriétaire (Workflow OTP)", tags = {"4.d Syndic - Copropriétaires"})
    @PostMapping("/co-owners")
    public ResponseEntity<String> addCoOwner(@RequestBody @Valid CreateCoOwnerDTO dto) {
        syndicOwnerService.addCoOwner(dto);
        return ResponseEntity.ok("Copropriétaire ajouté avec succès. Un code d'activation lui a été envoyé par email.");
    }

    @Operation(summary = "Lister les biens disponibles (VACANT) d'une résidence", tags = {"4.d Syndic - Copropriétaires"})
    @GetMapping("/residences/{residenceId}/properties/available")
    public ResponseEntity<List<PropertySummaryDTO>> getAvailableProperties(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(syndicOwnerService.getAvailableProperties(residenceId));
    }

    @Operation(summary = "Lister les résidences qui ont au moins un bien vacant", tags = {"4.d Syndic - Copropriétaires"})
    @GetMapping("/residences/with-vacant-properties")
    public ResponseEntity<List<ResidenceSummaryDTO>> getResidencesWithVacantProperties() {
        return ResponseEntity.ok(syndicOwnerService.getResidencesWithVacantProperties());
    }
}
