package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO pour la répartition des dépenses d'une résidence
 * Utilisé pour le graphique en camembert de l'onglet Finances
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExpenseBreakdownDTO {
    // Montant total du budget
    private BigDecimal totalAmount;

    // Liste des catégories de dépenses avec leurs montants et pourcentages
    private List<ExpenseCategoryDTO> categories;
}
