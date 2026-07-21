package com.example.solimus.dtos.admin.subscription;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO KPIS - GESTION DES ABONNEMENTS (page admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionKpiDTO {

    // Nombre d'abonnements actifs
    private long activeSubscriptions;

    // Variation en % des abonnements actifs vs il y a 30 jours
    private Double activeSubscriptionsVariation;

    // Nombre d'abonnements expirés
    private long expiredSubscriptions;

    // Variation en % des abonnements expirés vs il y a 30 jours
    private Double expiredSubscriptionsVariation;

    // Nombre d'abonnements à renouveler
    private long toRenewSubscriptions;

    // Revenus mensuels
    private BigDecimal monthlyRevenue;

    // Taux de renouvellement
    private Double renewalRate;
}
