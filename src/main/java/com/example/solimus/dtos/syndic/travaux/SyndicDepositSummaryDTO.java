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
}
