package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.tenant.profil.TenantProfileDTO;
import com.example.solimus.services.tenant.profil.TenantProfilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_LOCATAIRE')")
@Tag(name = "Locataire - Profil", description = "Consultation du profil du locataire")
public class TenantProfilController {

    private final TenantProfilService tenantProfilService;

    @Operation(summary = "Voir mon profil", tags = {"Locataire - Profil"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = TenantProfileDTO.class))),
            @ApiResponse(responseCode = "403", description = "Aucun bien assigné à ce compte locataire",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/profile")
    public ResponseEntity<TenantProfileDTO> getProfile() {
        return ResponseEntity.ok(tenantProfilService.getProfile());
    }
}
