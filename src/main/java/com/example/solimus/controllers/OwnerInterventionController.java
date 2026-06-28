package com.example.solimus.controllers;

import com.example.solimus.dtos.intervention.*;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import com.example.solimus.services.owner.OwnerInterventionService;
import com.example.solimus.services.minio.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/interventions")
@RequiredArgsConstructor
@Tag(name = "3.g Copropriétaire - Interventions", description = "Gestion des demandes de travaux par le copropriétaire")
public class OwnerInterventionController {

    private final OwnerInterventionService interventionService;
    private final MinioService minioService;




    // ==================== GESTION DES DEVIS ====================

    @Operation(summary = "Nombre de devis reçus pour une intervention")
    @GetMapping("/{interventionId}/quotes/count")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Map<String, Integer>> getQuotesCount(
            @PathVariable Long interventionId) {
        return ResponseEntity.ok(
            Map.of("total", interventionService.getQuotesCount(interventionId))
        );
    }

    @Operation(summary = "Lister les devis reçus pour une intervention")
    @GetMapping("/{interventionId}/quotes")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Page<CoOwnerQuoteCardDTO>> getQuotesByIntervention(
            @PathVariable Long interventionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
            interventionService.getQuotesByIntervention(interventionId, page, size)
        );
    }

   /** @Operation(summary = "Détail d'un devis")
    @GetMapping("/{interventionId}/quotes/{quoteId}")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<CoOwnerQuoteDetailDTO> getQuoteDetail(
            @PathVariable Long interventionId,
            @PathVariable Long quoteId) {
        return ResponseEntity.ok(
            interventionService.getQuoteDetail(interventionId, quoteId)
        );
    }
    */

    @Operation(summary = "Accepter un devis et valider le prestataire")
    @PostMapping("/{interventionId}/accept-quote/{quoteId}")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<String> acceptQuote(
            @PathVariable Long interventionId,
            @PathVariable Long quoteId) {
        interventionService.acceptQuote(interventionId, quoteId);
        return ResponseEntity.ok("Devis accepté avec succès. Le prestataire a été notifié.");
    }

    // ==================== PAIEMENTS ====================

    @Operation(summary = "Payer un acompte pour une intervention validée")
    @PostMapping("/{interventionId}/payer-acompte")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<PaymentResponseDTO> payerAcompte(
            @PathVariable Long interventionId,
            @RequestBody @Valid PayerAcompteDTO dto) {
        return ResponseEntity.ok(interventionService.payerAcompte(interventionId, dto));
    }

    @Operation(summary = "Valider les travaux et payer le solde")
    @PostMapping("/{interventionId}/valider-solde")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<PaymentResponseDTO> validerEtPayerSolde(
            @PathVariable Long interventionId,
            @RequestBody @Valid ValiderTravauxDTO dto) {
        return ResponseEntity.ok(interventionService.validerEtPayerSolde(interventionId, dto));
    }
}
