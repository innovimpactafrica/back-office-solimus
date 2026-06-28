package com.example.solimus.controllers;

import com.example.solimus.dtos.charge.ChargeAllocationDetailDTO;
import com.example.solimus.dtos.charge.ChargePaymentReceiptDTO;
import com.example.solimus.dtos.charge.ChargePaymentResponseDTO;
import com.example.solimus.dtos.charge.InitierPaiementChargeDTO;
import com.example.solimus.dtos.charge.MyChargesSummaryDTO;
import com.example.solimus.enums.ChargeStatus;
import com.example.solimus.services.owner.CoproprietaireChargeService;
import com.example.solimus.services.owner.CoOwnerChargePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coowner/charges")
@RequiredArgsConstructor
@Tag(name = "3.c Copropriétaire - Charges", description = "Consultation et paiement des charges de copropriété. Une charge peut être EN_ATTENTE, PAYEE ou EN_RETARD.")
public class OwnerChargeController {

    private final CoproprietaireChargeService chargeService;
    private final CoOwnerChargePaymentService paymentService;

    // ==================== CONSULTATION ====================

    @Operation(summary = "Dashboard — résumé + liste des charges (pagination + filtres)")
    @GetMapping
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<MyChargesSummaryDTO> getMesCharges(
            @Parameter(description = "Numéro de page (défaut: 0)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Taille de la page (défaut: 10)")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Filtrer par statut (EN_ATTENTE, PAYEE, EN_RETARD)")
            @RequestParam(required = false) ChargeStatus status,
            @Parameter(description = "Rechercher dans le titre de la charge")
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(chargeService.getMesCharges(page, size, status, search));
    }

    @Operation(summary = "Détail d'une charge allocation")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<ChargeAllocationDetailDTO> getDetail(
            @PathVariable Long id) {
        return ResponseEntity.ok(chargeService.getChargeDetail(id));
    }

    // ==================== PAIEMENT ====================

    @Operation(summary = "Initier le paiement d'une charge via TouchPay")
    @PostMapping("/{allocationId}/pay")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<ChargePaymentResponseDTO> payerCharge(
            @PathVariable Long allocationId,
            @RequestBody @Valid InitierPaiementChargeDTO dto) {
        return ResponseEntity.ok(
            paymentService.initierPaiement(allocationId, dto));
    }

    @Operation(summary = "Récupérer le reçu d'un paiement de charge")
    @GetMapping("/payment/{transactionRef}/receipt")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<ChargePaymentReceiptDTO> getReceipt(
            @PathVariable String transactionRef) {
        return ResponseEntity.ok(
            paymentService.getReceipt(transactionRef));
    }
}
