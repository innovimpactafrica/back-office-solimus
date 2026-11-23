package com.example.solimus.controllers;

import com.example.solimus.dtos.payment.proof.ProofBatchResultDTO;
import com.example.solimus.dtos.payment.proof.ProofRejectionRequestDTO;
import com.example.solimus.dtos.payment.proof.ProofValidationRequestDTO;
import com.example.solimus.dtos.provider.wallet.recharge.AdminDirectRechargeDTO;
import com.example.solimus.dtos.provider.wallet.recharge.AdminRechargePageDTO;
import com.example.solimus.dtos.provider.wallet.recharge.ProviderWalletRechargeDTO;
import com.example.solimus.enums.ProofStatus;
import com.example.solimus.services.provider.wallet.ProviderWalletRechargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/wallet/recharges")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Recharges des prestataires",
        description = "Valider les preuves de recharge des wallets prestataires, ou créditer directement.")
public class AdminWalletRechargeController {

    private final ProviderWalletRechargeService rechargeService;

    @Operation(summary = "Lister les recharges (badge : pendingValidationCount)")
    @GetMapping
    public ResponseEntity<AdminRechargePageDTO> list(
            @Parameter(description = "PENDING_VALIDATION, VALIDATED ou REJECTED ; toutes si absent")
            @RequestParam(required = false) ProofStatus status,
            @Parameter(description = "Référence RCH-…, nom, email ou téléphone du prestataire")
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(rechargeService.listForAdmin(status, search, page, size));
    }

    @Operation(summary = "Détail d'une recharge (avec le lien du justificatif)")
    @GetMapping("/{rechargeId}")
    public ResponseEntity<ProviderWalletRechargeDTO> detail(@PathVariable Long rechargeId) {
        return ResponseEntity.ok(rechargeService.getForAdmin(rechargeId));
    }

    @Operation(summary = "Valider une ou plusieurs recharges : les wallets sont crédités",
            description = "Body : {\"proofIds\": [ids des recharges]}. Chaque recharge est traitée indépendamment.")
    @PostMapping("/validate")
    public ResponseEntity<ProofBatchResultDTO> validate(@Valid @RequestBody ProofValidationRequestDTO body) {
        return ResponseEntity.ok(rechargeService.validate(body.getProofIds()));
    }

    @Operation(summary = "Rejeter une ou plusieurs recharges, avec le motif envoyé au prestataire",
            description = "Body : {\"proofIds\": [ids], \"reason\": \"…\"}. Aucun crédit.")
    @PostMapping("/reject")
    public ResponseEntity<ProofBatchResultDTO> reject(@Valid @RequestBody ProofRejectionRequestDTO body) {
        return ResponseEntity.ok(rechargeService.reject(body.getProofIds(), body.getReason()));
    }

    @Operation(summary = "Créditer directement le wallet d'un prestataire",
            description = "Paiement reçu hors application (espèces au bureau…). Crédit immédiat, tracé comme une recharge validée.")
    @PostMapping("/direct")
    public ResponseEntity<ProviderWalletRechargeDTO> creditDirectly(@Valid @RequestBody AdminDirectRechargeDTO body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rechargeService.creditDirectly(body));
    }
}
