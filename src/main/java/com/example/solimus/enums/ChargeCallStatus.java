package com.example.solimus.enums;

import lombok.Getter;

/**
 * Statut agrégé d'un ChargeCall, calculé à partir du statut
 * de paiement de ses ChargeCallItem.
 */
@Getter
public enum ChargeCallStatus {
    SENT("Envoyé"),       // Aucun paiement reçu
    PARTIAL("Partiel"),   // Au moins un paiement, mais pas total
    SETTLED("Soldé");     // Tous les items sont PAID

    private final String label;

    ChargeCallStatus(String label) {
        this.label = label;
    }

}
