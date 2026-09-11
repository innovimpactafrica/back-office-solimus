package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//DTO du récapitulatif affiché dans le modal "Acompte" après validation d'un devis
@Data
@Builder
public class SyndicDepositSummaryDTO {
    private String providerName;
    private String companyName;
    private BigDecimal totalAmount;
    private LocalDateTime emisLe;
    private BigDecimal walletBalanceAvailable;

    // true = le front doit afficher le menu "Poste budgétaire" et le rendre obligatoire
    // false = aucun poste à choisir, un poste a déjà été choisi avant (voir budgetItemLibelle)
    private boolean budgetItemRequired;

    // Libellé du poste déjà choisi (rempli seulement si budgetItemRequired = false)
    private String budgetItemLibelle;
}
