package com.example.solimus.enums;

/**
 * Statuts possibles d'une demande d'intervention dans le workflow Solimus.
 */
public enum InterventionStatus {
   PENDING("En attente de devis"),
   SYNDIC_ASSIGNED("Pris en charge par le syndic"),
   QUOTE_VALIDATED("Accepté"),
   STARTED("En cours"),
   FINISHED("Terminé"),
   FINAL_VALIDATION("Clôturé"),
   CANCELLED("Annulé");

    private final String label;

    InterventionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
