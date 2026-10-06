package com.example.solimus.enums;

import java.util.List;

/**
 * Statuts possibles d'une demande d'intervention dans le workflow Solimus.
 */
public enum InterventionStatus {
   PENDING("En attente"),
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

    // Statuts "ouverts" (tout sauf clôturé/annulé) — source unique, ne pas recopier cette liste ailleurs
    public static List<InterventionStatus> openStatuses() {
        return List.of(PENDING, SYNDIC_ASSIGNED, QUOTE_VALIDATED, STARTED, FINISHED);
    }
}
