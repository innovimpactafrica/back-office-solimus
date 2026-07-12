package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

//DTO principal du dashboard "Gestion des charges"
@Data
public class ChargeDashboardDTO {
    private BigDecimal annualBudget; // Somme des budgets ACTIVE de toutes les résidences du syndic
    private Integer activeResidencesCount; // Nombre de résidences ayant un budget ACTIVE
    private BigDecimal totalCalled; // Somme des ChargeCall générés (toutes résidences)
    private Integer chargeCallsCount; // Nombre d'appels de charges émis
    private Double totalCalledEvolutionPercent; // Évolution vs le mois dernier
    private BigDecimal totalCollected; // Somme des paiements reçus (ChargeCallPayment COMPLETED)
    private Double totalCollectedEvolutionPercent; // Évolution vs le mois dernier
    private BigDecimal unpaidAmount; // Somme des soldes restants non payés
    private Integer unpaidCoOwnersCount; // Nombre de copropriétaires ayant au moins un impayé
    private Double unpaidEvolutionPercent; // Évolution vs le mois dernier
    private List<MonthlyCollectionDTO> monthlyCollection; // Graphique "Encaissement mensuel"
    private BudgetPostesRepartitionDTO postesRepartition; // Camembert "Répartition des postes"
}