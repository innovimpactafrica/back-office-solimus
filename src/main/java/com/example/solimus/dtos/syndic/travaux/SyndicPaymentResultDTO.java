package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

//DTO de confirmation après paiement réussi (acompte ou solde final)
@Data
@Builder
public class SyndicPaymentResultDTO {
    private boolean success;
    private String message;
    private BigDecimal montantPaye;
    private BigDecimal nouveauSoldeWallet;
}
