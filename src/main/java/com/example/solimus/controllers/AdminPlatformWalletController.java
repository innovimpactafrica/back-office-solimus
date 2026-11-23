package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.wallet.PlatformWalletSummaryDTO;
import com.example.solimus.dtos.admin.wallet.PlatformWalletTransactionDTO;
import com.example.solimus.services.platform.PlatformWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/wallet")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Wallet Solimus", description = "Commissions encaissées par la plateforme sur les paiements de travaux")
public class AdminPlatformWalletController {

    private final PlatformWalletService platformWalletService;

    @Operation(summary = "Solde et totaux du wallet Solimus")
    @GetMapping
    public ResponseEntity<PlatformWalletSummaryDTO> getSummary() {
        return ResponseEntity.ok(platformWalletService.getSummary());
    }

    @Operation(summary = "Mouvements du wallet Solimus (commissions), plus récents d'abord")
    @GetMapping("/transactions")
    public ResponseEntity<Page<PlatformWalletTransactionDTO>> getTransactions(
            @Parameter(description = "Date de début incluse (yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Date de fin incluse (yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(platformWalletService.getTransactions(from, to, page, size));
    }
}
