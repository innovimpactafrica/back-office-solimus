package com.example.solimus.dtos.admin.finance;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc 1 : KPIs du dashboard "Admin > Finances" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceDashboardKpiDTO {

    private BigDecimal monthlyRevenue;
    // 0% si le mois précédent vaut 0 FCFA
    private Double monthlyRevenueVariation;

    private BigDecimal annualRevenue;
    // (Revenus de l'année / Prévision annuelle) x 100 — 0% si la prévision vaut 0
    private Double annualGoalPercentage;

    // Nombre de paiements validés (Syndic + Prestataire) du mois en cours
    private long paymentsReceivedCount;

    private BigDecimal syndicRevenue;
    // Part des revenus Syndics dans les revenus totaux du mois — 0% si le total vaut 0
    private Double syndicRevenueShare;

    private BigDecimal providerRevenue;
    private Double providerRevenueShare;

    // Évolution du trimestre en cours vs le trimestre précédent — 0% si le trimestre précédent vaut 0
    private Double growthRate;

    private long pendingPaymentsCount;
    private BigDecimal pendingPaymentsAmount;

    // Abonnements (Syndic + Prestataire) expirant dans les 30 prochains jours
    private long renewalsCount;
}
