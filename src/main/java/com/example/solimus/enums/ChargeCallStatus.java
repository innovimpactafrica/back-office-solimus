package com.example.solimus.enums;

/**
 * Statut agrégé d'un ChargeCall, calculé à partir du statut
 * de paiement de ses ChargeCallItem.
 */
public enum ChargeCallStatus {
    SENT("Envoyé"),       // Aucun paiement reçu
    PARTIAL("Partiel"),   // Au moins un paiement, mais pas total
    SETTLED("Soldé");     // Tous les items sont PAID

    private final String label;

    ChargeCallStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
