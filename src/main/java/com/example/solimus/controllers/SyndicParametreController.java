package com.example.solimus.controllers;

import com.example.solimus.dtos.residence.AddFacilityDTO;
import com.example.solimus.services.syndic.SyndicParametreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/syndic/parametres")
@RequiredArgsConstructor
@Tag(name = "4.b Syndic - Paramètres", description = "Gestion des paramètres et équipements communs par le syndic")
public class SyndicParametreController {

    private final SyndicParametreService syndicParametreService;

    @Operation(summary = "Ajouter un équipement commun", tags = {"4.b Syndic - Paramètres"})
    @PostMapping("/residences/{id}/facilities")
    public ResponseEntity<AddFacilityDTO> addFacility(
            @PathVariable Long id,
            @RequestBody @Valid AddFacilityDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(syndicParametreService.addFacility(id, dto));
    }
}
