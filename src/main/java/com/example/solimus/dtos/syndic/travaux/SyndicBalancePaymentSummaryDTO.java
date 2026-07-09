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
}
