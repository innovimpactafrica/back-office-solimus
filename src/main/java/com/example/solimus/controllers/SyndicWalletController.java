package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.wallet.*;
import com.example.solimus.services.syndic.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/syndic/wallet")
@RequiredArgsConstructor
@Tag(name = "Syndic - Wallet", description = "Gestion du portefeuille syndic")
public class SyndicWalletController {

    private final WalletService walletService;

    @Operation(summary = "Lister les résidences du syndic", description = "Récupère toutes les résidences gérées par le syndic connecté (id, nom)", tags = {"Syndic - Wallet"})
    @GetMapping("/residences")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<List<ResidenceSimpleDTO>> getSyndicResidences() {
        return ResponseEntity.ok(walletService.getSyndicResidences());
    }

    @Operation(summary = "Lister les postes budgétaires sans biens communs", description = "Récupère les postes budgétaires sans bien commun pour une résidence et l'année courante (id, libellé)", tags = {"Syndic - Wallet"})
    @GetMapping("/budget-items")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<List<BudgetItemSimpleDTO>> getBudgetItemsWithoutCommonFacility(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(walletService.getBudgetItemsWithoutCommonFacility(residenceId));
    }

    @Operation(summary = "Créer une demande de retrait", description = "Crée une nouvelle demande de retrait de fonds par le syndic", tags = {"Syndic - Wallet"})
    @PostMapping("/withdrawal-requests")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<Void> createWithdrawalRequest(@Valid @RequestBody CreateWithdrawalRequestDTO dto) {
        walletService.createWithdrawalRequest(dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Solde disponible du wallet syndic", description = "Retourne uniquement le solde disponible du wallet syndic", tags = {"Syndic - Wallet"})
    @GetMapping("/balance")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<WalletBalanceDTO> getWalletBalance() {
        return ResponseEntity.ok(walletService.getWalletBalance());
    }

    // ===== KPIs de la Vue d'ensemble =====
    @Operation(summary = "KPIs du portefeuille financier syndic (solde, charges collectées, prestataires, retraits en attente)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/kpis")
    public ResponseEntity<WalletKpiDTO> getWalletKpis(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWalletKpis(residenceId));
    }

    // ===== Graphique Recettes vs Dépenses =====
    @Operation(summary = "Graphique Recettes vs Dépenses (6 derniers mois glissants)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/chart")
    public ResponseEntity<WalletChartDTO> getWalletChart(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWalletChart(residenceId));
    }

    @Operation(summary = "Graphique Recettes vs Dépenses (4 trimestres de l'année civile en cours)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/chart/quarterly")
    public ResponseEntity<WalletChartDTO> getWalletChartQuarterly(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWalletChartQuarterly(residenceId));
    }

    @Operation(summary = "Aperçu des 4 résidences les plus récemment actives (widget Wallet)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences-overview")
    public ResponseEntity<WalletResidencesOverviewResponseDTO> getWalletResidencesOverview() {
        return ResponseEntity.ok(walletService.getWalletResidencesOverview());
    }

    @Operation(summary = "Aperçu des 5 derniers flux financiers (Vue d'ensemble Wallet)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/flows/overview")
    public ResponseEntity<WalletFlowOverviewResponseDTO> getWalletFlowsOverview(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWalletFlowsOverview(residenceId));
    }

    @Operation(summary = "Historique complet des transactions, paginé (onglet Transactions)")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/flows")
    public ResponseEntity<WalletFlowListResponseDTO> getWalletFlows(
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(walletService.getWalletFlows(residenceId, page, size));
    }

    @Operation(summary = "KPIs de l'onglet Retraits")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/withdrawals/kpis")
    public ResponseEntity<WithdrawalKpiDTO> getWithdrawalKpis(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWithdrawalKpis(residenceId));
    }

    @Operation(summary = "Détail complet d'une demande de retrait avec timeline")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/withdrawals/{id}")
    public ResponseEntity<WithdrawalDetailDTO> getWithdrawalDetail(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.getWithdrawalDetail(id));
    }

    @Operation(summary = "Historique paginé des demandes de retrait")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/withdrawals")
    public ResponseEntity<WithdrawalListResponseDTO> getWithdrawalsList(
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(walletService.getWithdrawalsList(residenceId, page, size));
    }
}
