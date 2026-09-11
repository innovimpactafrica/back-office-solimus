package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

//DTO du récapitulatif affiché dans le modal "Paiement" (solde final, syndic)
@Data
@Builder
public class SyndicBalancePaymentSummaryDTO {
    private BigDecimal montantDevis;
    private BigDecimal acompteVerse;
    private BigDecimal soldeRestant;
    private BigDecimal walletBalanceAvailable;

    // true = le front doit afficher le menu "Poste budgétaire" et le rendre obligatoire
    // false = aucun poste à choisir, un poste a déjà été choisi avant (voir budgetItemLibelle)
    private boolean budgetItemRequired;

    // Libellé du poste déjà choisi (rempli seulement si budgetItemRequired = false)
    private String budgetItemLibelle;
}
