package com.example.solimus.dtos.admin.dashboard;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc 1 : KPIs du dashboard admin global =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardKpiDTO {

    private long activeSyndics;
    // Toujours null pour l'instant : pas d'historique des changements de statut (User.status n'est
    // pas horodaté) — champ ajouté maintenant, rempli plus tard si un vrai suivi est mis en place
    private Double activeSyndicsVariation;

    private long activeProviders;
    private Double activeProvidersVariation;

    private long managedResidences;

    private long coOwners;

    private long activeSubscriptions;

    private long expiredSubscriptions;

    private BigDecimal monthlyRevenue;
    // 0% si le mois précédent vaut 0 FCFA (évite la division par zéro), jamais null ici
    private Double monthlyRevenueVariation;

    private BigDecimal annualRevenue;
    // (revenus encaissés depuis le 1er janvier / nombre de mois écoulés) x 12 — 0 si aucun revenu encaissé
    private BigDecimal annualForecast;
}
