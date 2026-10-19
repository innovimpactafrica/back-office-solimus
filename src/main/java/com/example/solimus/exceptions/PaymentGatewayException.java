package com.example.solimus.exceptions;

// Le prestataire de paiement (API TouchPay / GU) a refusé ou n'a pas pu traiter la demande → 502
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message) {
        super(message);
    }
}
