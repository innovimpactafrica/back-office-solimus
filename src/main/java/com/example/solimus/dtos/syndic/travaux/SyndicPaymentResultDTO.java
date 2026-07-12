package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

//DTO de confirmation après paiement (acompte ou solde final)
@Data
@Builder
public class SyndicPaymentResultDTO {
    private boolean success;
    private String message;
    private BigDecimal montantPaye;

    // Renseigné uniquement pour un paiement Wallet (nouveau solde après débit)
    private BigDecimal nouveauSoldeWallet;

    // Renseignés uniquement pour un paiement Mobile Money (TouchPay)
    private String transactionReference;
    private String paymentUrl;
}
