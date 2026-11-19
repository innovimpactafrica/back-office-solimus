package com.example.solimus.controllers;

import com.example.solimus.services.travaux.InterventionPaymentProofService;
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
@RequestMapping("/api/provider/payment-proofs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_PRESTATAIRE')")
@Tag(name = "Prestataire - Paiements reçus hors plateforme",
        description = "Le syndic ou le copropriétaire vous paie hors de l'application et déclare le paiement avec une preuve. "
                + "Vous confirmez la réception (acompte enregistré, ou solde : intervention clôturée) ou la rejetez.")
public class ProviderPaymentProofController {

    private final InterventionPaymentProofService proofService;

    @Operation(summary = "Lister les paiements déclarés à mon intention",
            description = "Filtres : status (PENDING_VALIDATION, VALIDATED, REJECTED), search (titre de l'intervention ou référence PTV-).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée",
                    content = @Content(schema = @Schema(implementation = ProviderPaymentProofPageDTO.class)))
    })
    @GetMapping
    public ResponseEntity<ProviderPaymentProofPageDTO> list(
            @RequestParam(required = false) ProofStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(proofService.getProofsForProvider(status, search, page, size));
    }

    @Operation(summary = "Détail d'un paiement déclaré (avec le lien du justificatif)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé",
                    content = @Content(schema = @Schema(implementation = InterventionPaymentProofDTO.class))),
            @ApiResponse(responseCode = "403", description = "Ce paiement ne vous est pas destiné",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Preuve introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{proofId}")
    public ResponseEntity<InterventionPaymentProofDTO> detail(@PathVariable Long proofId) {
        return ResponseEntity.ok(proofService.getProofForProvider(proofId));
    }

    @Operation(summary = "Confirmer la réception d'un ou plusieurs paiements",
            description = "Acompte : enregistré sur l'intervention. Solde : intervention clôturée (Clôturé). "
                    + "Chaque preuve est traitée indépendamment. Aucun wallet n'est mouvementé.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Traitement effectué (voir failures)",
                    content = @Content(schema = @Schema(implementation = ProofBatchResultDTO.class))),
            @ApiResponse(responseCode = "400", description = "Liste vide ou plus de 100 preuves",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/validate")
    public ResponseEntity<ProofBatchResultDTO> confirm(@Valid @RequestBody ProofValidationRequestDTO body) {
        return ResponseEntity.ok(proofService.confirmProofs(body.getProofIds()));
    }

    @Operation(summary = "Rejeter un ou plusieurs paiements déclarés (motif obligatoire)",
            description = "Le payeur est notifié avec le motif et peut déclarer de nouveau.")
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
