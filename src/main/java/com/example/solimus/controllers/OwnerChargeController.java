package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.owner.charge.*;
import com.example.solimus.enums.ChargeType;
import com.example.solimus.services.owner.charge.OwnerChargeService;
import com.example.solimus.services.charge.proof.ChargePaymentProofService;
import com.example.solimus.dtos.payment.proof.ChargePaymentProofDTO;
import com.example.solimus.enums.OffPlatformPaymentMethod;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
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
@Tag(name = "Copropriétaire - Charges", description = "Consultation des charges et envoi des preuves de paiement (paiement hors application, validé par le syndic)")
public class OwnerChargeController {

    private final OwnerChargeService chargeService;
    private final ChargePaymentProofService chargePaymentProofService;

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

    @Operation(summary = "[Désactivé] Initier un paiement de charge en ligne", deprecated = true,
            description = "Les charges se paient désormais hors de l'application : utiliser POST /{type}/{id}/payment-proof. "
                    + "Répond 400 tant que app.payments.charges-online.enabled=false.")
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

    // =========================================================================
    // PAIEMENT HORS PLATEFORME : PREUVE DE PAIEMENT
    // =========================================================================

    @Operation(summary = "Envoyer la preuve de paiement d'une charge (payée hors application)",
            description = "type = REGULAR (charge courante) ou EXCEPTIONAL. Le paiement solde toute la charge (reste dû). "
                    + "La charge passe à « En attente de validation » jusqu'à la décision du syndic : validée -> Payé, "
                    + "rejetée -> de nouveau à payer (motif notifié). Une seule preuve en attente par charge.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Preuve envoyée, en attente de validation du syndic",
                    content = @Content(schema = @Schema(implementation = ChargePaymentProofDTO.class))),
            @ApiResponse(responseCode = "400", description = "Charge déjà payée, rien à payer, budget clôturé, description ou fichier invalide, paiement en ligne en cours",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Cette charge ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Charge introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Une preuve est déjà en attente de validation pour cette charge",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/{type}/{id}/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChargePaymentProofDTO> submitPaymentProof(
            @PathVariable ChargeType type,
            @PathVariable Long id,
            @Parameter(description = "Description du paiement (3 à 1000 caractères)", required = true) @RequestParam String description,
            @Parameter(description = "VIREMENT, ESPECES, CHEQUE, WAVE, ORANGE_MONEY ou AUTRE (défaut AUTRE)")
            @RequestParam(required = false) OffPlatformPaymentMethod paymentMethod,
            @Parameter(description = "Date du paiement (yyyy-MM-dd), pas dans le futur") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
            @Parameter(description = "Justificatif : PDF, JPG ou PNG, 5 Mo maximum", required = true)
            @RequestPart("document") MultipartFile document) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chargePaymentProofService.submitProof(type, id, description, paymentMethod, paymentDate, document));
    }

    @Operation(summary = "Historique des preuves de paiement d'une charge", description = "Plus récente d'abord, avec statut et motif de rejet éventuel")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique renvoyé"),
            @ApiResponse(responseCode = "403", description = "Cette charge ne vous appartient pas",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{type}/{id}/payment-proofs")
    public ResponseEntity<List<ChargePaymentProofDTO>> getPaymentProofs(@PathVariable ChargeType type, @PathVariable Long id) {
        return ResponseEntity.ok(chargePaymentProofService.getMyProofs(type, id));
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
