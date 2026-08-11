package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.owner.charge.*;
import com.example.solimus.enums.ChargeType;
import com.example.solimus.services.owner.charge.OwnerChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coowner/charges")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_COPROPRIETAIRE')")
@Tag(name = "Copropriétaire - Charges", description = "Consultation et suivi des charges du copropriétaire")
public class OwnerChargeController {

    private final OwnerChargeService chargeService;

    // =========================================================================
    // LISTER MES CHARGES
    // =========================================================================

    @Operation(summary = "Lister mes charges", description = "Liste paginée des charges courantes et exceptionnelles, avec recherche et filtres")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = MyChargeListResponse.class)))
    })
    @GetMapping
    public ResponseEntity<MyChargeListResponse> getMyCharges(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ChargeType type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(chargeService.getMyCharges(search, type, status, residenceId, page, size));
    }

    @Operation(summary = "Lister mes résidences", description = "Liste des résidences où le copropriétaire a au moins un lot")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerResidenceDTO.class)))
    })
    @GetMapping("/residences")
    public ResponseEntity<List<OwnerResidenceDTO>> getMyResidences() {
        return ResponseEntity.ok(chargeService.getMyResidences());
    }

    // =========================================================================
    // DÉTAIL D'UNE CHARGE
    // =========================================================================

    @Operation(summary = "Détail d'une charge", description = "Retourne le détail complet d'une charge (type = REGULAR ou EXCEPTIONAL)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = MyChargeDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à accéder à cette charge",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Charge introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{type}/{id}")
    public ResponseEntity<MyChargeDetailDTO> getChargeDetail(
            @PathVariable ChargeType type,
            @PathVariable Long id) {
        return ResponseEntity.ok(chargeService.getChargeDetail(type, id));
    }

    // =========================================================================
    // PAIEMENT DE CHARGE
    // =========================================================================

    @Operation(summary = "Initier un paiement de charge", description = "Initie le paiement d'une charge via TouchPay")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement initié avec succès",
                    content = @Content(schema = @Schema(implementation = ChargePaymentResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Budget/appel exceptionnel clôturé, charge déjà payée, ou un paiement est déjà en cours pour cette charge",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'avez pas accès à cette charge",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Type de charge invalide, ou charge introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{type}/{id}/payment")
    public ResponseEntity<ChargePaymentResponseDTO> initierPaiement(
            @PathVariable ChargeType type,
            @PathVariable Long id,
            @RequestBody InitierPaiementChargeDTO dto) {
        return ResponseEntity.ok(chargeService.initierPaiement(type, id, dto));
    }

    @Operation(summary = "Récupérer le reçu d'un paiement", description = "Retourne le reçu d'un paiement à partir de sa référence")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reçu renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = ChargePaymentReceiptDTO.class))),
            @ApiResponse(responseCode = "403", description = "Ce paiement ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Référence de paiement invalide, ou paiement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/receipt/{transactionRef}")
    public ResponseEntity<ChargePaymentReceiptDTO> getReceipt(
            @PathVariable String transactionRef) {
        return ResponseEntity.ok(chargeService.getReceipt(transactionRef));
    }

    @Operation(summary = "Vérifie le statut réel d'un paiement de charge (appelé par l'app mobile au retour de la WebView)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = ChargePaymentStatusDTO.class))),
            @ApiResponse(responseCode = "403", description = "Ce paiement ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Référence de paiement invalide, ou paiement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/payment-status")
    public ResponseEntity<ChargePaymentStatusDTO> getPaymentStatus(
            @RequestParam String reference) {
        return ResponseEntity.ok(chargeService.getPaymentStatus(reference));
    }
}
