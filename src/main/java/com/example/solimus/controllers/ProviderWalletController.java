package com.example.solimus.controllers;

import com.example.solimus.dtos.provider.DemanderVersementDTO;
import com.example.solimus.dtos.provider.WithdrawalRequestDTO;
import com.example.solimus.dtos.provider.WalletDTO;
import com.example.solimus.services.provider.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/provider/wallet")
@RequiredArgsConstructor
@Tag(name = "5.c Prestataire - Wallet", description = "Gestion du portefeuille et des retraits")
public class ProviderWalletController {

    private final ProviderService providerService;

    @Operation(summary = "Récupérer les informations du portefeuille (Wallet)")
    @GetMapping
    public ResponseEntity<WalletDTO> getMonWallet() {
        return ResponseEntity.ok(providerService.getMonWallet());
    }

    @Operation(summary = "Demander un versement (Wave, Orange Money)")
    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawalRequestDTO> demanderVersement(
            @RequestBody @Valid DemanderVersementDTO dto) {
        return ResponseEntity.ok(providerService.demanderVersement(dto));
    }
}
