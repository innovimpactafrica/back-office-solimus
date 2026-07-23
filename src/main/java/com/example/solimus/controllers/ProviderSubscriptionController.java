package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.subscription.ProviderPlanDTO;
import com.example.solimus.dtos.provider.subscription.InitiateSubscriptionPaymentDTO;
import com.example.solimus.dtos.provider.subscription.SubscriptionPaymentResponseDTO;
import com.example.solimus.services.provider.subscription.SubscriptionPaymentService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Prestataire - Abonnement", description = "Gestion de l'abonnement du prestataire")
public class ProviderSubscriptionController {

    private final SubscriptionPaymentService subscriptionPaymentService;

    @Operation(summary = "Obtenir les formules d'abonnement actives, au choix du prestataire")
    @PreAuthorize("hasRole('ROLE_PROVIDER')")
    @GetMapping("/plan")
    public ResponseEntity<List<ProviderPlanDTO>> getProviderPlans() {
        return ResponseEntity.ok(subscriptionPaymentService.getProviderPlans());
    }

    @Operation(summary = "Initier le paiement d'un abonnement (mensuel ou annuel)")
    @PreAuthorize("hasRole('ROLE_PROVIDER')")
    @PostMapping("/initiate")
    public ResponseEntity<SubscriptionPaymentResponseDTO> initiatePayment(
            @Valid @RequestBody InitiateSubscriptionPaymentDTO dto) {
        return ResponseEntity.ok(subscriptionPaymentService.initiatePayment(dto));
    }
}
