package com.example.solimus.controllers;

import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.intervention.NearbyProviderDTO;
import com.example.solimus.dtos.intervention.SyndicQuoteDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.syndic.SyndicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "4.c Syndic - Interventions", description = "Gestion des interventions par le syndic")
public class SyndicInterventionController {

    private final SyndicService syndicService;


    @Operation(summary = "Trouver les prestataires proches (Rayon 30km + Spécialité)", tags = {"4.c Syndic - Interventions"})
    @GetMapping("/nearby-providers")
    public ResponseEntity<List<NearbyProviderDTO>> getNearbyProviders(
            @RequestParam Long residenceId,
            @RequestParam Long specialtyId) {
        return ResponseEntity.ok(syndicService.findNearbyProviders(residenceId, specialtyId));
    }


    @Operation(summary = "Accepter un devis et valider le prestataire", tags = {"4.c Syndic - Interventions"})
    @PostMapping("/interventions/{requestId}/accept-quote/{quoteId}")
    public ResponseEntity<String> acceptQuote(
            @PathVariable Long requestId,
            @PathVariable Long quoteId) {
        syndicService.acceptQuote(requestId, quoteId);
        return ResponseEntity.ok("Devis accepté avec succès. Le prestataire a été notifié.");
    }

    @Operation(summary = "Lister les devis reçus pour une demande", tags = {"4.c Syndic - Interventions"})
    @GetMapping("/interventions/{requestId}/quotes")
    public ResponseEntity<List<SyndicQuoteDTO>> getQuotesByIntervention(
            @PathVariable Long requestId) {
        return ResponseEntity.ok(syndicService.getQuotesByInterventionRequest(requestId));
    }

    @Operation(summary = "Lister mes demandes d'intervention", tags = {"4.c Syndic - Interventions"})
    @GetMapping("/interventions")
    public ResponseEntity<List<InterventionRequestDTO>> getMyInterventions() {
        return ResponseEntity.ok(syndicService.getMyInterventionRequests());
    }

    @Operation(summary = "Prendre en charge une intervention et diffuser aux prestataires", tags = {"4.c Syndic - Interventions"})
    @PostMapping("/interventions/{requestId}/assign")
    public ResponseEntity<InterventionRequestDTO> assignIntervention(@PathVariable Long requestId) {
        return ResponseEntity.ok(syndicService.assignIntervention(requestId));
    }

    // ==================== PAIEMENTS PAR LE SYNDIC ====================

    @Operation(summary = "Payer un acompte pour une intervention gérée par le syndic", tags = {"4.c Syndic - Interventions"})
    @PostMapping("/interventions/{requestId}/payer-acompte")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<PaymentResponseDTO> payerAcompte(
            @PathVariable Long requestId,
            @RequestBody @Valid PayerAcompteDTO dto) {
        return ResponseEntity.ok(syndicService.payerAcompte(requestId, dto));
    }

    @Operation(summary = "Valider les travaux et payer le solde", tags = {"4.c Syndic - Interventions"})
    @PostMapping("/interventions/{requestId}/valider-solde")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<PaymentResponseDTO> validerEtPayerSolde(
            @PathVariable Long requestId,
            @RequestBody @Valid ValiderTravauxDTO dto) {
        return ResponseEntity.ok(syndicService.validerEtPayerSolde(requestId, dto));
    }
}
