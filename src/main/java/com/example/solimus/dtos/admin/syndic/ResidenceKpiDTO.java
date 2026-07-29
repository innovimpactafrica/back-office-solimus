package com.example.solimus.dtos.admin.syndic;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc D-a "KPIs" de la fiche détail résidence (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidenceKpiDTO {

    // (lots occupés / total lots) x 100
    private Double occupancyRate;
    // Toujours null : aucun historique du statut des lots n'est conservé, "il y a un mois" n'est
    // donc pas reconstituable de façon fiable
    private Double occupancyRateEvolution;

    // (montant encaissé / montant facturé) x 100
    private Double collectionRate;
    private BigDecimal remainingToCollect;

    // Interventions dont le statut n'est ni FINAL_VALIDATION ni CANCELLED
    private long openIncidents;
    private long urgentIncidents;

    // Budget de l'année en cours (budget ACTIVE) — null si aucun budget actif pour cette résidence
    private BigDecimal annualBudget;
}
