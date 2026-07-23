package com.example.solimus.enums;

// Statut réel d'une ligne de charge (ExceptionalCallItem / ChargeCallItem),
// posé explicitement au moment où un paiement est confirmé — jamais déduit
// par comparaison arithmétique (quotePart - paidAmount).
public enum ChargeItemPaymentStatus {

    PENDING("En attente"),
    PARTIALLY_PAID("Partiellement payé"),
    PAID("Payé");

    private final String label;

    ChargeItemPaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}