package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.tenant.accueil.TenantDashboardDTO;
import com.example.solimus.services.tenant.accueil.TenantAccueilService;
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
@Tag(name = "Locataire - Accueil", description = "Dashboard d'accueil du locataire")
public class TenantAccueilController {

    private final TenantAccueilService tenantAccueilService;

    @Operation(summary = "Dashboard d'accueil du locataire", tags = {"Locataire - Accueil"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = TenantDashboardDTO.class))),
            @ApiResponse(responseCode = "403", description = "Aucun bien assigné à ce compte locataire",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/dashboard")
    public ResponseEntity<TenantDashboardDTO> getDashboard() {
        return ResponseEntity.ok(tenantAccueilService.getDashboard());
    }
}
