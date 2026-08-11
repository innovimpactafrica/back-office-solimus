package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.provider.travaux.ProviderTravauxDetailDTO;
import com.example.solimus.dtos.provider.travaux.ProviderTravauxPageDTO;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.services.provider.travaux.ProviderTravauxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@RestController
@RequestMapping("/api/provider/travaux")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('ROLE_PRESTATAIRE')")
@Tag(name = "Prestataire - Travaux", description = "Travaux du prestataire (demandes dont le devis a été accepté)")
public class ProviderTravauxController {

    private final ProviderTravauxService providerTravauxService;

    @Operation(summary = "Lister mes travaux (devis accepté, en cours, terminés, clôturés)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ProviderTravauxPageDTO.class)))
    })
    @GetMapping
    public ResponseEntity<ProviderTravauxPageDTO> getMyWorks(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(providerTravauxService.getMyWorks(search, status, page, size));
    }

    @Operation(summary = "Voir le détail d'une intervention assignée")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = ProviderTravauxDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à consulter cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProviderTravauxDetailDTO> getWorkDetails(@PathVariable Long id) {
        return ResponseEntity.ok(providerTravauxService.getWorkDetails(id));
    }

    @Operation(summary = "Démarrer une intervention (passer de QUOTE_VALIDATED à STARTED)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Travaux démarrés avec succès"),
            @ApiResponse(responseCode = "400", description = "Les travaux ne peuvent être démarrés que si le statut est 'Devis validé'",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Seul le prestataire sélectionné par le syndic peut démarrer cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Demande d'intervention introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{id}/start")
    public ResponseEntity<String> startIntervention(@PathVariable Long id) {
        providerTravauxService.startIntervention(id);
        return ResponseEntity.ok("Les travaux ont été démarrés avec succès.");
    }

    @Operation(summary = "Terminer une intervention (passer de STARTED à FINISHED)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Intervention marquée comme terminée avec succès"),
            @ApiResponse(responseCode = "400", description = "L'intervention n'est pas en cours",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à terminer cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Demande introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/{id}/finish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> finishIntervention(
            @PathVariable Long id,
            @RequestParam(required = false) String commentaire,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        providerTravauxService.finishIntervention(id, commentaire, photos);
        return ResponseEntity.ok("L'intervention a été marquée comme terminée.");
    }
}
