package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour le total des paiements collectés par mois
 * Utilisé pour le graphique d'évolution des paiements dans l'onglet Finances
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyPaymentDTO {
    // Numéro du mois (1 = janvier, 12 = décembre)
    private Integer month;

    // Montant total collecté pour ce mois (zéro si aucun paiement)
    private BigDecimal amount;
}
