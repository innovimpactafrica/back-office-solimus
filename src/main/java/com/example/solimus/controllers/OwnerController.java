package com.example.solimus.controllers;

import com.example.solimus.dtos.residence.PropertyDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;
import com.example.solimus.services.owner.OwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coowner")
@RequiredArgsConstructor
@Tag(name = "3.a Copropriétaire - Inscription", description = "Endpoints publics pour récupérer les résidences et biens lors de l'inscription. Aucune authentification requise.")
public class OwnerController {

    private final OwnerService OwnerService;

    @Operation(summary = "Lister toutes les résidences disponibles")
    @GetMapping("/residences")
    public ResponseEntity<List<ResidenceDTO>> getAllResidences() {
        return ResponseEntity.ok(OwnerService.getAllResidences());
    }

    @Operation(summary = "Lister les biens d'une résidence")
    @GetMapping("/residences/{residenceId}/properties")
    public ResponseEntity<List<PropertyDTO>> getPropertiesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(OwnerService.getPropertiesByResidence(residenceId));
    }
}
