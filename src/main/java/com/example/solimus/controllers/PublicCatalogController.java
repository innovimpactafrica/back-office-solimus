package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.services.specialty.SpecialtyCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Référentiels consultables SANS connexion (écrans d'inscription de l'application mobile).
 * Lecture seule : la gestion des spécialités se fait par le syndic (/api/syndic/settings/specialties).
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Public - Référentiels", description = "Listes accessibles sans connexion, pour l'inscription (application mobile)")
public class PublicCatalogController {

    private final SpecialtyCatalogService specialtyCatalogService;

    @Operation(summary = "Lister les spécialités (catégories de travaux) — sans connexion",
            description = "Liste complète triée par nom, pour que le prestataire choisisse sa spécialité à l'inscription "
                    + "(champ specialtyId de POST /api/auth/register). Il ne recevra que les demandes de travaux de cette "
                    + "spécialité. 'search' filtre sur le nom (facultatif).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SpecialtyDTO.class))))
    })
    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyDTO>> listSpecialties(
            @Parameter(description = "Filtre sur le nom, ex. « plomb »") @RequestParam(required = false) String search) {
        return ResponseEntity.ok(specialtyCatalogService.listAll(search));
    }
}
