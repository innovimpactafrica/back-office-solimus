package com.example.solimus.dtos.syndic.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DTO — une ligne du tableau "Historique des paiements" de la page "Mon abonnement" du syndic
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyndicSubscriptionHistoryDTO {

    private LocalDateTime date;
    private String planName;
    private BigDecimal amount;
    private String paymentMethodLabel;
    private String statusLabel;
}
