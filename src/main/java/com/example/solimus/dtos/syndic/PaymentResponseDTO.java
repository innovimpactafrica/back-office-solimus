package com.example.solimus.dtos.syndic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de réponse pour l'initiation d'un paiement via TouchPay.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDTO {

    // Indique si la transaction a été initiée avec succès côté backend
    private boolean success;

    // Message d'information pour le frontend
    private String message;

    // Référence de transaction unique générée par le système
    private String transactionReference;

    // Montant à payer
    private BigDecimal amountToPay;

    // URL du pont TouchPay à ouvrir dans la WebView
    private String paymentUrl;
}
