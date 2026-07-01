package com.example.solimus.enums;

/**
 * Statut de paiement individuel d'un copropriétaire pour un ChargeCall donné.
 */
public enum ChargeCallItemStatus {
    PENDING("En attente"),   // Aucun paiement effectué
    PARTIAL("Partiel"),      // Paiement partiel effectué
    PAID("Payé"),           // Paiement complet effectué
    OVERDUE("En retard");   //En retard

    private final String label;

    ChargeCallItemStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
