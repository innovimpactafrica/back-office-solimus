package com.example.solimus.controllers;

import com.example.solimus.dtos.property.PropertyDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;
import com.example.solimus.services.coowner.CoOwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coowner")
@RequiredArgsConstructor
@Tag(name = "CoOwner API", description = "Endpoints pour les copropriétaires (dropdowns pour l'inscription)")
public class CoOwnerController {

    private final CoOwnerService coOwnerService;

    @Operation(summary = "Lister toutes les résidences (dropdown pour l'inscription copropriétaire)")
    @GetMapping("/residences")
    public ResponseEntity<List<ResidenceDTO>> getAllResidences() {
        return ResponseEntity.ok(coOwnerService.getAllResidences());
    }

    @Operation(summary = "Lister les biens d'une résidence (dropdown pour l'inscription copropriétaire)")
    @GetMapping("/residences/{residenceId}/properties")
    public ResponseEntity<List<PropertyDTO>> getPropertiesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(coOwnerService.getPropertiesByResidence(residenceId));
    }
}
