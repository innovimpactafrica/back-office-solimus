package com.example.solimus.dtos.syndic.dashboard;

import lombok.Data;
import java.math.BigDecimal;

//DTO du Tableau de Bord principal, filtré par résidence
@Data
public class MainDashboardDTO {

    private BigDecimal treasuryTotal; // Trésorerie disponible (via wallet)
    private Double treasuryEvolutionPercent; // Évolution vs mois dernier

    private Double recoveryRate; // Taux de recouvrement en %
    private Double recoveryRateEvolutionPercent; // Évolution vs mois dernier (en points de %)

    private BigDecimal unpaidAmount; // Montant total impayé pour cette résidence
    private Double unpaidEvolutionPercent; // Évolution vs mois dernier

    private Integer managedResidencesCount; // Nombre de résidences gérées (global syndic, pas filtré)
    private Integer totalLotsCount; // Nombre total de lots (global syndic, pas filtré)

    private Long openIncidentsCount; // Nombre d'incidents ouverts (hors FINAL_VALIDATION/CANCELLED)
    private Long urgentIncidentsCount; // Nombre d'incidents urgents parmi les ouverts

    private Long todayInterventionsCount; // Nombre d'interventions créées aujourd'hui
    private Long plannedInterventionsCount; // Nombre d'interventions encore en attente de devis (PENDING) créées aujourd'hui
}