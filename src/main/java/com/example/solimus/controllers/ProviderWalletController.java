package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.provider.wallet.RequestWithdrawalDTO;
import com.example.solimus.dtos.provider.wallet.WithdrawalRequestDTO;
import com.example.solimus.dtos.provider.wallet.WalletDTO;
import com.example.solimus.services.provider.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Portefeuille renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = WalletDTO.class)))
    })
    @GetMapping
    public ResponseEntity<WalletDTO> getMyWallet(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(walletService.getMyWallet(page, size));
    }

    @Operation(summary = "Demander un versement (Wave, Orange Money)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande de versement enregistrée avec succès",
                    content = @Content(schema = @Schema(implementation = WithdrawalRequestDTO.class))),
            @ApiResponse(responseCode = "400", description = "Solde disponible insuffisant",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawalRequestDTO> requestWithdrawal(
            @RequestBody @Valid RequestWithdrawalDTO dto) {
        return ResponseEntity.ok(walletService.requestWithdrawal(dto));
    }
}
