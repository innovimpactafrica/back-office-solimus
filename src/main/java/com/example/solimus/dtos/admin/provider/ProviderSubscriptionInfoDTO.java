package com.example.solimus.dtos.admin.provider;

import com.example.solimus.enums.SubscriptionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO — Bloc E "Abonnement actuel" de la fiche détail prestataire (Admin) =====
// subscriptionId permet au front de réutiliser directement les endpoints génériques déjà en place
// (/api/admin/subscriptions/subscribers/{subscriptionId}/...?subscriberType=PRESTATAIRE) pour
// Suspendre/Renouveler, exactement comme pour le syndic.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSubscriptionInfoDTO {

    private Long subscriptionId;
    private String planName;
    private String durationLabel;
    // Montant réellement payé — déjà le bon tarif mensuel ou annuel selon la durée choisie
    private BigDecimal amount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private long daysRemaining;

    // Statut réel de l'abonnement, tel qu'enregistré (jamais déduit de daysRemaining)
    private SubscriptionStatus status;
    private String statusLabel;
}
