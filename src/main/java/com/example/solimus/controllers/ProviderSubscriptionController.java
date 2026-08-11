package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.subscription.ProviderPlanDTO;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.provider.subscription.InitiateSubscriptionPaymentDTO;
import com.example.solimus.dtos.provider.subscription.SubscriptionPaymentResponseDTO;
import com.example.solimus.services.provider.subscription.SubscriptionPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/provider/subscription")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_PRESTATAIRE')")
@Tag(name = "Prestataire - Abonnement", description = "Gestion de l'abonnement du prestataire")
public class ProviderSubscriptionController {

    private final SubscriptionPaymentService subscriptionPaymentService;

    @Operation(summary = "Obtenir les formules d'abonnement actives, au choix du prestataire")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ProviderPlanDTO.class)))
    })
    @GetMapping("/plan")
    public ResponseEntity<List<ProviderPlanDTO>> getProviderPlans() {
        return ResponseEntity.ok(subscriptionPaymentService.getProviderPlans());
    }

    @Operation(summary = "Initier le paiement d'un abonnement (mensuel ou annuel)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement initié avec succès",
                    content = @Content(schema = @Schema(implementation = SubscriptionPaymentResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Compte non prestataire, paiement déjà en attente, abonnement déjà actif, "
                    + "formule non disponible, ou tarif non configuré pour la durée choisie",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Formule d'abonnement introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/initiate")
    public ResponseEntity<SubscriptionPaymentResponseDTO> initiatePayment(
            @Valid @RequestBody InitiateSubscriptionPaymentDTO dto) {
        return ResponseEntity.ok(subscriptionPaymentService.initiatePayment(dto));
    }
}
