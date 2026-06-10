package com.example.solimus.controllers;

import com.example.solimus.dtos.subscription.SouscrirePremiumDTO;
import com.example.solimus.dtos.subscription.SubscriptionDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.subscription.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/provider/subscription")
@RequiredArgsConstructor
@Tag(
        name = "5.a Prestataire - Abonnement",
        description = "Gestion de l'abonnement Premium. GRATUIT : 3 devis/mois. PREMIUM : 35 000 FCFA/mois, devis illimités, priorité."
)
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    @Operation(summary = "Voir mon abonnement actuel")
    @GetMapping
    public ResponseEntity<SubscriptionDTO> getMonAbonnement() {
        return ResponseEntity.ok(subscriptionService.getMonAbonnement(getCurrentUser().getId()));
    }

    @Operation(
            summary = "Passer au plan Premium",
            description = "Lance le paiement de 35 000 FCFA via TouchPay. Retourne une URL à ouvrir dans une WebView. Le Premium est activé automatiquement après confirmation du paiement."
    )
    @PostMapping("/premium")
    public ResponseEntity<PaymentResponseDTO> passerEnPremium(@RequestBody @Valid SouscrirePremiumDTO dto) {
        return ResponseEntity.ok(subscriptionService.passerEnPremium(getCurrentUser().getId(), dto));
    }

    @Operation(
            summary = "Désactiver le renouvellement automatique",
            description = "L'abonnement reste actif jusqu'à la date d'expiration. Il ne sera plus renouvelé automatiquement le mois suivant."
    )
    @PostMapping("/cancel")
    public ResponseEntity<String> annulerAbonnement() {
        subscriptionService.annulerAbonnement(getCurrentUser().getId());
        return ResponseEntity.ok("Renouvellement automatique désactivé.");
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
