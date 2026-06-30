package com.example.solimus.controllers;

import com.example.solimus.dtos.provider.ProviderDashboardDTO;
import com.example.solimus.services.provider.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/accueil")
@RequiredArgsConstructor
@Tag(name = "Prestataire - Accueil", description = "Dashboard et statistiques du prestataire")
public class ProviderAccueilController {

    private final ProviderService providerService;

    @Operation(summary = "Récupérer les données consolidées du tableau de bord (Dashboard)")
    @GetMapping("/dashboard")
    public ResponseEntity<ProviderDashboardDTO> getDashboard() {
        return ResponseEntity.ok(providerService.getDashboard());
    }
}
