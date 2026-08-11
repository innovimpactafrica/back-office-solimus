package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.wallet.*;
import com.example.solimus.services.syndic.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@PreAuthorize("hasAuthority('ROLE_SYNDIC') and @planFeatureGuard.hasFeature('WALLET_MANAGEMENT')")
@Tag(name = "Syndic - Wallet", description = "Gestion du portefeuille syndic")
public class SyndicWalletController {

    private final WalletService walletService;


    @Operation(summary = "Créer une demande de retrait", description = "Crée une nouvelle demande de retrait de fonds par le syndic", tags = {"Syndic - Wallet"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande de retrait créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Ce poste budgétaire est lié à un bien commun (à gérer via le module Travaux), "
                    + "ou n'appartient pas à la résidence spécifiée",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à effectuer un retrait pour cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence ou poste budgétaire introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/withdrawal-requests")
    public ResponseEntity<Void> createWithdrawalRequest(@Valid @RequestBody CreateWithdrawalRequestDTO dto) {
        walletService.createWithdrawalRequest(dto);
        return ResponseEntity.ok().build();
    }


    @Operation(summary = "Lister les postes budgétaires sans biens communs", description = "Récupère les postes budgétaires sans bien commun pour une résidence et l'année courante (id, libellé)", tags = {"Syndic - Wallet"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = BudgetItemSimpleDTO.class)))
    })
    @GetMapping("/budget-items")
    public ResponseEntity<List<BudgetItemSimpleDTO>> getBudgetItemsWithoutCommonFacility(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(walletService.getBudgetItemsWithoutCommonFacility(residenceId));
    }


    @Operation(summary = "Lister les résidences du syndic", description = "Récupère toutes les résidences gérées par le syndic connecté (id, nom)", tags = {"Syndic - Wallet"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ResidenceSimpleDTO.class)))
    })
    @GetMapping("/residences")
    public ResponseEntity<List<ResidenceSimpleDTO>> getSyndicResidences() {
        return ResponseEntity.ok(walletService.getSyndicResidences());
    }


    @Operation(summary = "Solde disponible du wallet syndic", description = "Retourne uniquement le solde disponible du wallet syndic", tags = {"Syndic - Wallet"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solde renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = WalletBalanceDTO.class)))
    })
    @GetMapping("/balance")
    public ResponseEntity<WalletBalanceDTO> getWalletBalance() {
        return ResponseEntity.ok(walletService.getWalletBalance());
    }

    // ===== KPIs de la Vue d'ensemble =====
    @Operation(summary = "KPIs du portefeuille financier syndic (solde, charges collectées, prestataires, retraits en attente)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPIs renvoyés avec succès",
                    content = @Content(schema = @Schema(implementation = WalletKpiDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cette résidence ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/kpis")
    public ResponseEntity<WalletKpiDTO> getWalletKpis(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWalletKpis(residenceId));
    }

    // ===== Graphique Recettes vs Dépenses =====
    @Operation(summary = "Graphique Recettes vs Dépenses (6 derniers mois glissants)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Graphique renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = WalletChartDTO.class)))
    })
    @GetMapping("/chart")
    public ResponseEntity<WalletChartDTO> getWalletChart(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWalletChart(residenceId));
    }

    @Operation(summary = "Graphique Recettes vs Dépenses (4 trimestres de l'année civile en cours)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Graphique renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = WalletChartDTO.class)))
    })
    @GetMapping("/chart/quarterly")
    public ResponseEntity<WalletChartDTO> getWalletChartQuarterly(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWalletChartQuarterly(residenceId));
    }

    @Operation(summary = "Aperçu des 4 résidences les plus récemment actives (widget Wallet)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aperçu renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = WalletResidencesOverviewResponseDTO.class)))
    })
    @GetMapping("/residences-overview")
    public ResponseEntity<WalletResidencesOverviewResponseDTO> getWalletResidencesOverview() {
        return ResponseEntity.ok(walletService.getWalletResidencesOverview());
    }

    @Operation(summary = "Aperçu des 5 derniers flux financiers (Vue d'ensemble Wallet)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aperçu renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = WalletFlowOverviewResponseDTO.class)))
    })
    @GetMapping("/flows/overview")
    public ResponseEntity<WalletFlowOverviewResponseDTO> getWalletFlowsOverview(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWalletFlowsOverview(residenceId));
    }

    @Operation(summary = "Historique complet des transactions, paginé (onglet Transactions)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = WalletFlowListResponseDTO.class)))
    })
    @GetMapping("/flows")
    public ResponseEntity<WalletFlowListResponseDTO> getWalletFlows(
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(walletService.getWalletFlows(residenceId, page, size));
    }

    @Operation(summary = "KPIs de l'onglet Retraits")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPIs renvoyés avec succès",
                    content = @Content(schema = @Schema(implementation = WithdrawalKpiDTO.class)))
    })
    @GetMapping("/withdrawals/kpis")
    public ResponseEntity<WithdrawalKpiDTO> getWithdrawalKpis(
            @RequestParam(required = false) Long residenceId) {
        return ResponseEntity.ok(walletService.getWithdrawalKpis(residenceId));
    }

    @Operation(summary = "Détail complet d'une demande de retrait avec timeline")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = WithdrawalDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cette demande de retrait ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Demande de retrait introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/withdrawals/{id}")
    public ResponseEntity<WithdrawalDetailDTO> getWithdrawalDetail(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.getWithdrawalDetail(id));
    }

    @Operation(summary = "Historique paginé des demandes de retrait")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = WithdrawalListResponseDTO.class)))
    })
    @GetMapping("/withdrawals")
    public ResponseEntity<WithdrawalListResponseDTO> getWithdrawalsList(
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(walletService.getWithdrawalsList(residenceId, page, size));
    }
}
