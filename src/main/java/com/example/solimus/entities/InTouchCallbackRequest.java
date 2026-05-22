package com.example.solimus.entities;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// InTouchCallbackRequest.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InTouchCallbackRequest {

    // Notre référence — celle qu'on a générée (PAY-123456, SUB-123456, WIT-123456)
    @JsonProperty("partner_transaction_id")
    private String partnerTransactionId;

    // Référence InTouch de leur côté
    @JsonProperty("gu_transaction_id")
    private String guTransactionId;

    // Statut du paiement : "SUCCESSFUL" ou "FAILED"
    @JsonProperty("status")
    private String status;

    // Message descriptif (ex: "Transaction successful")
    @JsonProperty("message")
    private String message;

    // Montant de la transaction
    @JsonProperty("amount")
    private BigDecimal amount;

    // Numéro de téléphone utilisé
    @JsonProperty("phone_number")
    private String phoneNumber;
}


