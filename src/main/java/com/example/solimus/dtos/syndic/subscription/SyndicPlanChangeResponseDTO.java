package com.example.solimus.dtos.syndic.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// DTO — réponse à l'initiation d'un changement de formule : lien de paiement TouchPay à ouvrir
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyndicPlanChangeResponseDTO {

    private boolean success;
    private String message;
    private String transactionReference;
    private BigDecimal amount;
    private String paymentUrl;
}
