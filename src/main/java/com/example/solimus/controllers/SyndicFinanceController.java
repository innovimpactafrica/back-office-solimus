package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.shared.ExcelFileDTO;
import com.example.solimus.dtos.syndic.charge.PaymentListResponse;
import com.example.solimus.dtos.syndic.finance.FinanceDashboardDTO;
import com.example.solimus.dtos.syndic.finance.RecentPaymentDTO;
import com.example.solimus.dtos.syndic.finance.UnpaidListResponse;
import com.example.solimus.dtos.syndic.residence.WalletTransactionDTO;
import com.example.solimus.enums.WalletTransactionCategory;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.services.syndic.finance.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @Operation(summary = "Liste des paiements (module Finances)", description = "Aligné sur /api/syndic/budget/payments — filtres optionnels par résidence, année et recherche nom copropriétaire", tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = PaymentListResponse.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/payments")
    public ResponseEntity<PaymentListResponse> getFinancePayments(
            @Parameter(description = "Filtre optionnel sur une résidence précise — absent = toutes résidences du syndic")
            @RequestParam(required = false) Long residenceId,
            @Parameter(description = "Filtre optionnel par année — absent = toutes années confondues", example = "2026")
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(financeService.getFinancePayments(residenceId, year, page, size, search));
    }

    @Operation(summary = "Export Excel des paiements", description = "Mêmes filtres que /payments — toutes les lignes filtrées, sans pagination", tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichier .xlsx généré avec succès"),
            @ApiResponse(responseCode = "400", description = "Format d'export non supporté",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/payments/export")
    public ResponseEntity<byte[]> exportFinancePayments(
            @Parameter(description = "Filtre optionnel sur une résidence précise — absent = toutes résidences du syndic")
            @RequestParam(required = false) Long residenceId,
            @Parameter(description = "Filtre optionnel par année — absent = toutes années confondues", example = "2026")
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "xlsx") String format) {
        validateExcelFormat(format);
        return toExcelDownload(financeService.exportPayments(residenceId, year, search));
    }

    @Operation(summary = "Liste des impayés (module Finances)", description = "Aligné sur /api/syndic/budget/unpaid — filtres optionnels par résidence et année", tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = UnpaidListResponse.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/unpaid")
    public ResponseEntity<UnpaidListResponse> getFinanceUnpaid(
            @Parameter(description = "Filtre optionnel sur une résidence précise — absent = toutes résidences du syndic")
            @RequestParam(required = false) Long residenceId,
            @Parameter(description = "Filtre optionnel par année — absent = toutes années confondues", example = "2026")
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(financeService.getFinanceUnpaid(residenceId, year, page, size));
    }

    @Operation(summary = "Export Excel des impayés", description = "Mêmes filtres que /unpaid — toutes les lignes filtrées, sans pagination", tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichier .xlsx généré avec succès"),
            @ApiResponse(responseCode = "400", description = "Format d'export non supporté",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/unpaid/export")
    public ResponseEntity<byte[]> exportFinanceUnpaid(
            @Parameter(description = "Filtre optionnel sur une résidence précise — absent = toutes résidences du syndic")
            @RequestParam(required = false) Long residenceId,
            @Parameter(description = "Filtre optionnel par année — absent = toutes années confondues", example = "2026")
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "xlsx") String format) {
        validateExcelFormat(format);
        return toExcelDownload(financeService.exportUnpaid(residenceId, year));
    }

    // =========================================================================
    // TRANSACTIONS WALLET (historique complet, "Voir l'historique complet")
    // =========================================================================

    @Operation(summary = "Historique complet des transactions du wallet",
            description = "Filtres optionnels par résidence, catégorie et année — pagination réelle",
            tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = WalletTransactionDTO.class)))
    })
    @GetMapping("/wallet-transactions")
    public ResponseEntity<Page<WalletTransactionDTO>> getWalletTransactions(
            @RequestParam(required = false) Long residenceId,
            @RequestParam(required = false) WalletTransactionCategory category,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(financeService.getWalletTransactions(residenceId, category, year, page, size));
    }

    @Operation(summary = "Export Excel des transactions du wallet", description = "Mêmes filtres que /wallet-transactions — toutes les lignes filtrées, sans pagination", tags = {"Syndic - Finances"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichier .xlsx généré avec succès"),
            @ApiResponse(responseCode = "400", description = "Format d'export non supporté",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Résidence introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/wallet-transactions/export")
    public ResponseEntity<byte[]> exportWalletTransactions(
            @RequestParam(required = false) Long residenceId,
            @RequestParam(required = false) WalletTransactionCategory category,
            @Parameter(description = "Filtre optionnel par année — absent = toutes années confondues", example = "2026")
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "xlsx") String format) {
        validateExcelFormat(format);
        return toExcelDownload(financeService.exportWalletTransactions(residenceId, category, year));
    }

    // =========================================================================
    // UTILITAIRES EXPORT EXCEL
    // =========================================================================

    // Seul le format xlsx est supporté pour l'instant — le paramètre "format" est prévu pour une
    // extension future (ex: csv/pdf) sans casser le contrat d'URL
    private void validateExcelFormat(String format) {
        if (format != null && !format.equalsIgnoreCase("xlsx")) {
            throw new BadRequestException("Seul le format d'export xlsx est supporté actuellement");
        }
    }

    private ResponseEntity<byte[]> toExcelDownload(ExcelFileDTO file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.getFileName()).build());
        return ResponseEntity.ok().headers(headers).body(file.getContent());
    }
}
