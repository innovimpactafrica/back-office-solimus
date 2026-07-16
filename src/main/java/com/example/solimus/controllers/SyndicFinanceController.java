package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.finance.FinanceDashboardDTO;
import com.example.solimus.dtos.syndic.finance.FinancePaymentRowDTO;
import com.example.solimus.dtos.syndic.finance.RecentPaymentDTO;
import com.example.solimus.dtos.syndic.finance.UnpaidListResponse;
import com.example.solimus.services.syndic.finance.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/syndic/finances")
@RequiredArgsConstructor
public class SyndicFinanceController {

    private final FinanceService financeService;

    // =========================================================================
    // DASHBOARD "FINANCES"
    // =========================================================================

    @Operation(summary = "Dashboard 'Finances'", description = "Trésorerie, charges collectées, impayés, dépenses + graphique cumulatif", tags = {"Syndic - Finances"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/dashboard")
    public ResponseEntity<FinanceDashboardDTO> getFinanceDashboard() {
        return ResponseEntity.ok(financeService.getFinanceDashboard());
    }

    @Operation(summary = "Paiements récents", description = "Derniers paiements reçus, toutes résidences confondues", tags = {"Syndic - Finances"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/recent-payments")
    public ResponseEntity<List<RecentPaymentDTO>> getRecentPayments(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(financeService.getRecentPayments(limit));
    }

    // =========================================================================
    // LISTE DES PAIEMENTS (module Finances, historique complet)
    // =========================================================================

    @Operation(summary = "Liste des paiements (module Finances)", description = "Historique paginé de tous les paiements de charges reçus", tags = {"Syndic - Finances"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/payments")
    public ResponseEntity<Page<FinancePaymentRowDTO>> getFinancePayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(financeService.getFinancePayments(page, size));
    }

    @Operation(summary = "Liste des impayés (module Finances)", description = "Historique paginé de tous les impayés de charges", tags = {"Syndic - Finances"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/unpaid")
    public ResponseEntity<UnpaidListResponse> getFinanceUnpaid(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(financeService.getFinanceUnpaid(page, size));
    }
}
