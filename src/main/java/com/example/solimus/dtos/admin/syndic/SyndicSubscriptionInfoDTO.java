package com.example.solimus.dtos.admin.syndic;

import com.example.solimus.enums.SubscriptionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ===== DTO — Bloc C "Abonnement" de la fiche détail syndic (Admin) =====
// subscriptionId permet au front de réutiliser directement les endpoints génériques déjà en place
// (/api/admin/subscriptions/subscribers/{subscriptionId}/...?subscriberType=SYNDIC) pour Suspendre/Renouveler
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicSubscriptionInfoDTO {

    private Long subscriptionId;
    private String planName;
    private String durationLabel;
    // Montant réellement payé (déjà le bon tarif mensuel ou annuel selon la durée choisie à la souscription)
    private BigDecimal amount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private long daysRemaining;

    // Statut réel de l'abonnement, tel qu'enregistré (jamais déduit de daysRemaining — gère
    // correctement les abonnements suspendus par l'admin)
    private SubscriptionStatus status;
    private String statusLabel;
}
