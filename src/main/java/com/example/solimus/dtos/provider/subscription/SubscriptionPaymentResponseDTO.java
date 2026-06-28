package com.example.solimus.dtos.provider.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// DTO pour la réponse après initiation d'un paiement d'abonnement
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionPaymentResponseDTO {

    private boolean success; // Indique si la demande a été acceptée
    private String message; // Message d'information pour l'utilisateur
    private String transactionReference; // Référence unique de la transaction
    private BigDecimal amount; // Montant à payer
    private String paymentUrl; // URL de redirection vers TouchPay pour finaliser le paiement
}