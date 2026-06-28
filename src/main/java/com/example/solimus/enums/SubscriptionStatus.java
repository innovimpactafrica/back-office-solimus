package com.example.solimus.enums;

public enum SubscriptionStatus {
    PENDING,    // paiement initié, en attente de confirmation TouchPay
    ACTIVE,     // paiement confirmé, accès total à la plateforme
    EXPIRED,    // date de fin dépassée, accès révoqué
    CANCELLED ,  // annulé (par l'admin ou le prestataire)
    FAILED,     // le paiement a échoué techniquement chez TouchPay
}