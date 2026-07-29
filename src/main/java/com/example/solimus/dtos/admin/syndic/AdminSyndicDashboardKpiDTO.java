package com.example.solimus.dtos.admin.syndic;

import lombok.*;

// ===== DTO — KPIs de la page "Gestion des syndics" (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSyndicDashboardKpiDTO {

    private long totalSyndics;
    // Variation vs il y a 30 jours, null si aucun syndic n'existait déjà à cette date (division par zéro)
    private Double totalSyndicsVariation;

    private long activeSyndics;
    // Toujours null : il n'existe pas d'historique des changements de statut (User.status n'est pas
    // horodaté), donc "actifs il y a 30 jours" ne peut pas être reconstitué de façon fiable
    private Double activeSyndicsVariation;

    private long suspendedSyndics;

    private long expiringIn30Days;

    private long totalResidences;
}
