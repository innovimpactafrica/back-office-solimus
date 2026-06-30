package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.travaux.PayDepositDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import com.example.solimus.services.syndic.SyndicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "Syndic - Paiements", description = "Gestion des paiements par le syndic")
public class SyndicPaymentController {

    private final SyndicService syndicService;

    @Operation(summary = "Payer un acompte pour une intervention validée", tags = {"Syndic - Paiements"})
    @PostMapping("/interventions/{id}/payer-acompte")
    public ResponseEntity<PaymentResponseDTO> payerAcompte(
            @PathVariable Long id,
            @RequestBody @Valid PayDepositDTO dto) {
        return ResponseEntity.ok(syndicService.payerAcompte(id, dto));
    }

    @Operation(summary = "Valider les travaux et payer le solde", tags = {"Syndic - Paiements"})
    @PostMapping("/interventions/{id}/valider-solde")
    public ResponseEntity<PaymentResponseDTO> validateAndPayBalance(
            @PathVariable Long id,
            @RequestBody @Valid ValiderTravauxDTO dto) {
        return ResponseEntity.ok(syndicService.validateAndPayBalance(id, dto));
    }
}
