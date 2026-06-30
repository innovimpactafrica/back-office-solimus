package com.example.solimus.dtos.provider.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

// DTO de l'écran "Mon abonnement" : carte de l'abonnement actuel + historique des paiements
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MySubscriptionDTO {

    // --- Carte de l'abonnement actuel ---
    private String planName;         // Plan d'abonnement
    private boolean active;          // true si abonnement actuellement valide
    private String status;           //  "Actif" / "Expiré" / "Aucun"
    private LocalDateTime startDate; // Date d'activation
    private LocalDateTime endDate;   // Date d'expiration
    private String paymentMethod;    // Méthode de paiement

    // --- Historique des paiements (paginé) ---
    private Page<SubscriptionHistoryItemDTO> paymentHistory;
}
