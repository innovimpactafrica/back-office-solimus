package com.example.solimus.controllers;

import com.example.solimus.dtos.provider.wallet.RequestWithdrawalDTO;
import com.example.solimus.dtos.provider.wallet.WithdrawalRequestDTO;
import com.example.solimus.dtos.provider.wallet.WalletDTO;
import com.example.solimus.services.provider.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/provider/wallet")
@RequiredArgsConstructor
@Tag(name = "Prestataire - Wallet", description = "Gestion du portefeuille et des retraits")
public class ProviderWalletController {

    private final WalletService walletService;

    @Operation(summary = "Récupérer les informations du portefeuille (Wallet)")
    @GetMapping
    public ResponseEntity<WalletDTO> getMyWallet(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(walletService.getMyWallet(page, size));
    }

    @Operation(summary = "Demander un versement (Wave, Orange Money)")
    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawalRequestDTO> requestWithdrawal(
            @RequestBody @Valid RequestWithdrawalDTO dto) {
        return ResponseEntity.ok(walletService.requestWithdrawal(dto));
    }
}
