package com.example.solimus.controllers;

import com.example.solimus.dtos.dashboard.CoOwnerDashboardDTO;
import com.example.solimus.services.owner.dashboard.CoOwnerDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coowner")
@RequiredArgsConstructor
@Tag(name = "Copropriétaire - Dashboard", description = "Dashboard du copropriétaire")
public class OwnerDashboardController {

    private final CoOwnerDashboardService dashboardService;

    @Operation(summary = "Récupérer les données du dashboard")
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<CoOwnerDashboardDTO> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard(null));
    }
}
