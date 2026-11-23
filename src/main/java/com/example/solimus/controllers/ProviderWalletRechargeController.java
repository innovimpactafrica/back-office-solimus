package com.example.solimus.controllers;

import com.example.solimus.dtos.provider.wallet.recharge.ProviderWalletRechargeDTO;
import com.example.solimus.dtos.provider.wallet.recharge.RechargeInfoDTO;
import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.ProofStatus;
import com.example.solimus.services.provider.wallet.ProviderWalletRechargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/provider/wallet/recharges")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_PRESTATAIRE')")
@Tag(name = "Prestataire - Recharge du compte",
        description = "Recharger son wallet : payer Solimus hors application, envoyer la preuve, Solimus valide et crédite.")
public class ProviderWalletRechargeController {

    private final ProviderWalletRechargeService rechargeService;

    @Operation(summary = "Écran de recharge : solde, montant manquant pour voir les demandes, instructions de paiement")
    @GetMapping("/info")
    public ResponseEntity<RechargeInfoDTO> info() {
        return ResponseEntity.ok(rechargeService.getInfo());
    }

    @Operation(summary = "Envoyer une preuve de recharge",
            description = "Après avoir payé Solimus hors application. Le wallet est crédité quand Solimus valide la preuve. "
                    + "Montant entier en FCFA, entre le minimum et le maximum de /info ; 3 recharges en attente au plus.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProviderWalletRechargeDTO> submit(
            @Parameter(description = "Montant payé (FCFA)", required = true) @RequestParam BigDecimal amount,
            @Parameter(description = "Description (3 à 1000 caractères), ex. « Wave du 23/11, réf. TX123 »", required = true)
            @RequestParam String description,
            @Parameter(description = "VIREMENT, ESPECES, CHEQUE, WAVE, ORANGE_MONEY ou AUTRE (défaut AUTRE)")
            @RequestParam(required = false) OffPlatformPaymentMethod paymentMethod,
            @Parameter(description = "Date du paiement (yyyy-MM-dd), pas dans le futur ; aujourd'hui par défaut")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
            @Parameter(description = "Justificatif : PDF, JPG ou PNG, 5 Mo maximum", required = true)
            @RequestPart("document") MultipartFile document) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rechargeService.submit(amount, paymentMethod, paymentDate, description, document));
    }

    @Operation(summary = "Mes recharges (plus récentes d'abord)")
    @GetMapping
    public ResponseEntity<Page<ProviderWalletRechargeDTO>> myRecharges(
            @Parameter(description = "PENDING_VALIDATION, VALIDATED ou REJECTED ; toutes si absent")
            @RequestParam(required = false) ProofStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(rechargeService.getMyRecharges(status, page, size));
    }
}
