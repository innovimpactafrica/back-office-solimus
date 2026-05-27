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
@Tag(name = "Provider Subscription", description = "Actions d'abonnement réservées aux Prestataires")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    @Operation(summary = "Consulter les détails de mon abonnement")
    @GetMapping
    public ResponseEntity<SubscriptionDTO> getMonAbonnement() {
        User user = getCurrentUser();
        return ResponseEntity.ok(subscriptionService.getMonAbonnement(user.getId()));
    }

    @Operation(summary = "Passer à l'abonnement Premium")
    @PostMapping("/premium")
    public ResponseEntity<PaymentResponseDTO> passerEnPremium(@RequestBody @Valid SouscrirePremiumDTO dto) {
        User user = getCurrentUser();
        return ResponseEntity.ok(subscriptionService.passerEnPremium(user.getId(), dto));
    }

    @Operation(summary = "Désactiver le renouvellement automatique de l'abonnement")
    @PostMapping("/cancel")
    public ResponseEntity<String> annulerAbonnement() {
        User user = getCurrentUser();
        subscriptionService.annulerAbonnement(user.getId());
        return ResponseEntity.ok("Renouvellement automatique désactivé avec succès.");
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Prestataire non trouvé"));
    }
}
