package com.example.solimus.dtos.owner.travaux;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Récapitulatif financier affiché avant le paiement du solde (écran "Paiement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSummaryDTO {

    // ID de l'intervention concernée
    private Long interventionId;

    // Montant total du devis accepté (ex: 75 000)
    private BigDecimal montantDevis;

    // Acompte déjà versé (ex: 35 000)
    private BigDecimal acompteVerse;

    // Solde restant à payer (ex: 40 000)
    private BigDecimal soldeRestant;
}
