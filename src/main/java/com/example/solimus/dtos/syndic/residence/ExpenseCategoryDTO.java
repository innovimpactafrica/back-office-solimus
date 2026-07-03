package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour une catégorie de dépense dans la répartition du budget
 * Utilisé pour le graphique en camembert de l'onglet Finances
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExpenseCategoryDTO {
    // Libellé de la catégorie (ex: "Entretien", "Sécurité", etc.)
    private String label;

    // Montant total pour cette catégorie
    private BigDecimal amount;

    // Pourcentage du budget total (arrondi)
    private Double percentage;
}
