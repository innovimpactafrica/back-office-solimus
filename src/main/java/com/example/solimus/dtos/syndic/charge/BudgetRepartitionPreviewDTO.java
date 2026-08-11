package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aperçu de la répartition d'un budget pas encore créé — écran "Nouveau Budget Prévisionnel"
 * (Étape 2 — Postes budgétaires). Même structure de ligne (CoOwnerQuotePartDTO) que celle
 * affichée après création (BudgetDetailDTO), pour garantir que l'aperçu et le résultat final
 * sont visuellement identiques.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetRepartitionPreviewDTO {

    // Somme des montants des postes saisis
    private BigDecimal budgetTotal;

    // "PAR MOIS" ou "PAR TRIMESTRE", selon la fréquence de charges du syndic
    private String periodeLabel;

    private BigDecimal totalTantieme;

    private BigDecimal totalQuotePartPeriode;

    private List<CoOwnerQuotePartDTO> repartition;
}
