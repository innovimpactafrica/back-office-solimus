package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

//DTO de la page de détail d'un budget (KPIs + tableau des postes)
@Data
public class BudgetOverviewDTO {

    private Long id;

    private String reference; // Ex: BUD-2026-001

    private Integer annee;

    private String status; // DRAFT, ACTIVE, CLOSED

    private String residenceName;

    // --- KPI 1 : Budget total prévu ---
    private BigDecimal budgetTotal;

    // --- KPI 2 : Dépenses réelles GLOBALES ---
    // Calculées à partir des SyndicWalletTransaction (catégorie TRAVAUX, résidence + année du budget).
    // C'est la SEULE vraie donnée calculée de la page — tout le reste des "réels" par poste est provisoire (voir BudgetItemOverviewDTO).
    private BigDecimal depensesReellesGlobal;

    // --- KPI 3 : Écart budgétaire = budgetTotal - depensesReellesGlobal ---
    private BigDecimal ecartBudgetaire;

    // --- KPI 4 : Pourcentage de consommation = depensesReellesGlobal / budgetTotal * 100 ---
    private Integer consommationPercentage;

    // --- Tableau des postes budgétaires ---
    private List<BudgetItemOverviewDTO> items;
}