package com.example.solimus.controllers;

import com.example.solimus.enums.ChargeType;
import com.example.solimus.services.charge.proof.ChargePaymentProofService;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.payment.proof.*;
import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.ProofStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/syndic/charges/payment-proofs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SYNDIC') and @planFeatureGuard.hasFeature('CHARGE_MANAGEMENT')")
@Tag(name = "Syndic - Validation des paiements de charges",
        description = "Les charges sont payées hors de l'application : le copropriétaire envoie une preuve, "
                + "le syndic la valide (charge Payée) ou la rejette (charge de nouveau à payer).")
public class SyndicChargePaymentProofController {

    private final ChargePaymentProofService proofService;

    @Operation(summary = "Lister les preuves de paiement de charges",
            description = "Toutes résidences du syndic. Filtres : status (PENDING_VALIDATION, VALIDATED, REJECTED), "
                    + "residenceId, chargeType (REGULAR, EXCEPTIONAL), search (nom du copropriétaire ou référence PRV-). "
                    + "pendingValidationCount = preuves à traiter, sans filtre.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée",
                    content = @Content(schema = @Schema(implementation = SyndicChargeProofPageDTO.class)))
    })
    @GetMapping
    public ResponseEntity<SyndicChargeProofPageDTO> list(
            @RequestParam(required = false) ProofStatus status,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(required = false) ChargeType chargeType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(proofService.getProofsForSyndic(status, residenceId, chargeType, search, page, size));
    }

    @Operation(summary = "Détail d'une preuve de paiement (avec le lien du justificatif)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé",
                    content = @Content(schema = @Schema(implementation = ChargePaymentProofDTO.class))),
            @ApiResponse(responseCode = "403", description = "Preuve d'une résidence qui n'est pas la vôtre",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Preuve introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{proofId}")
    public ResponseEntity<ChargePaymentProofDTO> detail(@PathVariable Long proofId) {
        return ResponseEntity.ok(proofService.getProofForSyndic(proofId));
    }

    @Operation(summary = "Valider une ou plusieurs preuves : les charges passent à Payé",
            description = "Chaque preuve est traitée indépendamment (succeededIds / failures). À la validation : "
                    + "paiement enregistré (référence CPY-/ECP-, hors plateforme), charge Payée, statut du lot et "
                    + "santé de la résidence recalculés, copropriétaire notifié. Le wallet du syndic n'est pas crédité "
                    + "(les fonds n'ont pas transité par Solimus).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Traitement effectué (voir failures pour les preuves non traitées)",
                    content = @Content(schema = @Schema(implementation = ProofBatchResultDTO.class))),
            @ApiResponse(responseCode = "400", description = "Liste vide ou plus de 100 preuves",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/validate")
    public ResponseEntity<ProofBatchResultDTO> validate(@Valid @RequestBody ProofValidationRequestDTO body) {
        return ResponseEntity.ok(proofService.validateProofs(body.getProofIds()));
    }

    @Operation(summary = "Rejeter une ou plusieurs preuves (motif obligatoire)",
            description = "La charge redevient « En attente » et le copropriétaire est notifié avec le motif.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Traitement effectué",
                    content = @Content(schema = @Schema(implementation = ProofBatchResultDTO.class))),
            @ApiResponse(responseCode = "400", description = "Liste vide ou motif absent",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/reject")
    public ResponseEntity<ProofBatchResultDTO> reject(@Valid @RequestBody ProofRejectionRequestDTO body) {
        return ResponseEntity.ok(proofService.rejectProofs(body.getProofIds(), body.getReason()));
    }
}
