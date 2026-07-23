package com.example.solimus.enums;

public enum SubscriptionStatus {
    PENDING("En attente"),        // paiement initié, en attente de confirmation TouchPay
    ACTIVE("Actif"),              // paiement confirmé, accès total à la plateforme
    EXPIRED("Expiré"),            // date de fin dépassée, accès révoqué
    CANCELLED("Annulé"),         // annulé (par l'admin ou le prestataire)
    DESACTIVATED("Désactivé"),   // désactivé temporairement par l'admin
    FAILED("Échoué");             // le paiement a échoué techniquement chez TouchPay

    private final String label;

    SubscriptionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}