package com.example.solimus.enums;

/**
 * Statuts possibles d'une demande d'intervention dans le workflow Solimus.
 */
public enum InterventionStatus {
   PENDING("Signalé"),
   SYNDIC_ASSIGNED("Pris en charge"),
   QUOTE_SENT("Devis"),
   SYNDIC_VALIDATED("Assigné"),
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
