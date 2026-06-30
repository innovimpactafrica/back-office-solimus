package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO complet retourné après création (ou consultation) d'un budget.
 * Correspond à l'écran "Étape 2 sur 2" une fois le budget enregistré :
 * postes budgétaires + répartition finale par copropriétaire.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetDetailDTO {

    private Long id;
    private String residenceName;
    private Integer annee;
    private BigDecimal budgetTotal;          // Somme des items → "Budget total estimé"

    private List<BudgetItemDTO> items;       // Postes budgétaires
    private List<CoOwnerQuotePartDTO> repartition; // Répartition par owner avec montants

    // Libellé de la colonne période Ex: "PAR TRIMESTRE" ou "PAR MOIS"
    private String periodeLabel;

    private BigDecimal totalTantieme;        // Doit toujours faire 100
    private BigDecimal totalQuotePartPeriode; // Somme colonne "Par trimestre/mois" → ligne Total
}
