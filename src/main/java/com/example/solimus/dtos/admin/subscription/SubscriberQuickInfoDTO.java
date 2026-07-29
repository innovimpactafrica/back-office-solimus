package com.example.solimus.dtos.admin.subscription;

import com.example.solimus.enums.SubscriptionStatus;
import lombok.*;

// ===== DTO — bloc "Détails du client" affiché en haut des modales d'action (Suspendre, Réactiver...) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberQuickInfoDTO {

    private String clientName;

    // "Syndic" / "Prestataire" 
    private String subscriberTypeLabel;

    private String planName;

    private SubscriptionStatus status;
    private String statusLabel;
}