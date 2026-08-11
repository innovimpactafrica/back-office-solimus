package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.finance.FinanceDashboardDTO;
import com.example.solimus.dtos.syndic.finance.FinancePaymentRowDTO;
import com.example.solimus.dtos.syndic.finance.RecentPaymentDTO;
import com.example.solimus.dtos.syndic.finance.UnpaidListResponse;
import com.example.solimus.services.syndic.finance.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/syndic/finances")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SYNDIC') and @planFeatureGuard.hasFeature('CHARGE_MANAGEMENT')")
@Tag(name = "Syndic - Finances", description = "Dashboard financier, paiements et impayés")
public class SyndicFinanceController {

    private final FinanceService financeService;

    // =========================================================================
    // DASHBOARD "FINANCES"
    // =========================================================================

    @Operation(summary = "Dashboard 'Finances'", description = "Trésorerie, charges collectées, impayés, dépenses + graphique cumulatif", tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = FinanceDashboardDTO.class)))
    })
    @GetMapping("/dashboard")
    public ResponseEntity<FinanceDashboardDTO> getFinanceDashboard() {
        return ResponseEntity.ok(financeService.getFinanceDashboard());
    }

    @Operation(summary = "Paiements récents", description = "Derniers paiements reçus, toutes résidences confondues", tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = RecentPaymentDTO.class)))
    })
    @GetMapping("/recent-payments")
    public ResponseEntity<List<RecentPaymentDTO>> getRecentPayments(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(financeService.getRecentPayments(limit));
    }

    // =========================================================================
    // LISTE DES PAIEMENTS (module Finances, historique complet)
    // =========================================================================

    @Operation(summary = "Liste des paiements (module Finances)", description = "Historique paginé de tous les paiements de charges reçus", tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = FinancePaymentRowDTO.class)))
    })
    @GetMapping("/payments")
    public ResponseEntity<Page<FinancePaymentRowDTO>> getFinancePayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(financeService.getFinancePayments(page, size));
    }

    @Operation(summary = "Liste des impayés (module Finances)", description = "Historique paginé de tous les impayés de charges", tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = UnpaidListResponse.class)))
    })
    @GetMapping("/unpaid")
    public ResponseEntity<UnpaidListResponse> getFinanceUnpaid(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(financeService.getFinanceUnpaid(page, size));
    }
}
