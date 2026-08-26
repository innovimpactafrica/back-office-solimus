package com.example.solimus.enums;

// Statut d'une ligne de charge, posé au moment du paiement (jamais déduit par calcul)
public enum ChargeItemPaymentStatus {

    PENDING("En attente"),
    PAID("Payé"),
    NO_AMOUNT_DUE("Rien à payer"); // Quote-part = 0 à la création

    private final String label;

    ChargeItemPaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}