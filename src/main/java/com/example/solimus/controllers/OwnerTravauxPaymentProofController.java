package com.example.solimus.controllers;

import com.example.solimus.enums.ProofPayerType;
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
@RequestMapping("/api/owner/travaux")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_COPROPRIETAIRE')")
@Tag(name = "Copropriétaire - Paiement des travaux hors plateforme",
        description = "Le copropriétaire (quand il a accepté le devis) paie le prestataire hors de l'application puis déclare l'acompte ou le solde avec une preuve. "
                + "Le prestataire confirme la réception : acompte enregistré, ou solde -> intervention clôturée.")
public class OwnerTravauxPaymentProofController {

    private static final String METHOD_DOC = "VIREMENT, ESPECES, CHEQUE, WAVE, ORANGE_MONEY ou AUTRE (défaut AUTRE)";
    private static final String FILE_DOC = "Justificatif du paiement : PDF, JPG ou PNG, 5 Mo maximum";

    private final InterventionPaymentProofService proofService;

    @Operation(summary = "Déclarer l'acompte payé hors plateforme (avec preuve)",
            description = "Conditions : prestataire choisi, devis accepté, aucun acompte déjà enregistré, 0 < amount ≤ TTC du devis. "
                    + "La preuve attend la confirmation du prestataire.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Déclaration enregistrée, en attente de confirmation",
                    content = @Content(schema = @Schema(implementation = InterventionPaymentProofDTO.class))),
            @ApiResponse(responseCode = "400", description = "Montant invalide, acompte déjà versé, fichier invalide, paiement en ligne en cours",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas le payeur de cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Un acompte déclaré attend déjà la confirmation du prestataire",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/interventions/{interventionId}/deposit/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InterventionPaymentProofDTO> declareDeposit(
            @PathVariable Long interventionId,
            @Parameter(description = "Montant de l'acompte versé (FCFA)", required = true) @RequestParam BigDecimal amount,
            @Parameter(description = "Description du paiement (3 à 1000 caractères)", required = true) @RequestParam String description,
            @Parameter(description = METHOD_DOC) @RequestParam(required = false) OffPlatformPaymentMethod paymentMethod,
            @Parameter(description = "Date du paiement (yyyy-MM-dd), pas dans le futur") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
            @Parameter(description = FILE_DOC, required = true) @RequestPart("document") MultipartFile document) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proofService.declareDeposit(ProofPayerType.COPROPRIETAIRE,
                interventionId, amount, description, paymentMethod, paymentDate, document));
    }

    @Operation(summary = "Déclarer le solde payé hors plateforme (avec preuve)",
            description = "Conditions : travaux terminés, aucun acompte en attente de confirmation. Montant = TTC − acompte, "
                    + "calculé par le serveur. À la confirmation du prestataire, l'intervention est clôturée.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Déclaration enregistrée, en attente de confirmation",
                    content = @Content(schema = @Schema(implementation = InterventionPaymentProofDTO.class))),
            @ApiResponse(responseCode = "400", description = "Travaux non terminés, aucun solde, acompte en attente, fichier invalide",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas le payeur de cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Un solde déclaré attend déjà la confirmation du prestataire",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/interventions/{interventionId}/balance/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InterventionPaymentProofDTO> declareBalance(
            @PathVariable Long interventionId,
            @Parameter(description = "Description du paiement (3 à 1000 caractères)", required = true) @RequestParam String description,
            @Parameter(description = METHOD_DOC) @RequestParam(required = false) OffPlatformPaymentMethod paymentMethod,
            @Parameter(description = "Date du paiement (yyyy-MM-dd), pas dans le futur") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate,
            @Parameter(description = FILE_DOC, required = true) @RequestPart("document") MultipartFile document) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proofService.declareBalance(ProofPayerType.COPROPRIETAIRE,
                interventionId, description, paymentMethod, paymentDate, document));
    }

    @Operation(summary = "Historique des paiements déclarés pour une intervention (acompte et solde)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique renvoyé, plus récent d'abord"),
            @ApiResponse(responseCode = "403", description = "Accès non autorisé à cette intervention",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/interventions/{interventionId}/payment-proofs")
    public ResponseEntity<List<InterventionPaymentProofDTO>> history(@PathVariable Long interventionId) {
        return ResponseEntity.ok(proofService.getProofsForPayer(ProofPayerType.COPROPRIETAIRE, interventionId));
    }
}
