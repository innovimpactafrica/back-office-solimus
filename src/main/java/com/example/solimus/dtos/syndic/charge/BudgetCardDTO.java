package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

//DTO d'une carte de budget (une résidence)
@Data
public class BudgetCardDTO {

    private Long id;

    private String reference; // Ex: BUD-2026-001

    private String status; // DRAFT, ACTIVE, CLOSED

    private Integer annee;

    private String residenceName;

    private String repartitionModeLabel; // Libellé lisible du mode de répartition, ex: "Tantièmes"

    private Integer totalPostes; // Nombre total de postes du budget (items.size())

    private BigDecimal budgetTotal;

    private List<BudgetItemPreviewDTO> topItems; // Les 4 premiers postes affichés sur la carte

    private Integer autresPostesCount; // Nombre de postes restants au-delà des 4 affichés (0 si <= 4)

    // Dépenses réelles GLOBALES du budget (transactions TRAVAUX de la résidence sur l'année)
    private BigDecimal depensesReelles;

    // Pourcentage de consommation = depensesReelles / budgetTotal * 100
    private Integer depensesReellesPercentage;
}