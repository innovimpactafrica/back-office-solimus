package com.example.solimus.dtos.admin.provider;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — KPIs de la page "Gestion des prestataires" (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProviderDashboardKpiDTO {

    private long totalProviders;
    // Variation vs il y a 30 jours, null si aucun prestataire n'existait déjà à cette date
    private Double totalProvidersVariation;

    private long activeProviders;
    // Toujours null : pas d'historique des changements de statut (voir SyndicDashboardKpiDTO, même limite)
    private Double activeProvidersVariation;

    private long suspendedProviders;

    private long expiringIn30Days;

    // Somme de tous les montants payés sur les abonnements prestataire, depuis toujours (pas de fenêtre de période)
    private BigDecimal totalRevenue;
}
