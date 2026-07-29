package com.example.solimus.dtos.admin.subscription;

import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — bloc "Détails du client" affiché en haut de la modale "Renouveler l'abonnement" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberRenewalInfoDTO {

    private String clientName;

    // "Syndic" / "Prestataire"
    private String subscriberTypeLabel;

    private String currentPlanName;

    private LocalDateTime expirationDate;

    private long daysRemaining; // Nombre de jours restants avant expiration calculé à partir de la date d'expiration et de la date du jour
}