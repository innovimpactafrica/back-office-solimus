package com.example.solimus.enums;

// Statut d'un équipement commun, calculé à la volée depuis ses interventions liées
// (jamais persisté) — voir SyndicResidenceServiceImpl.calculateFacilityStatus
public enum CommonFacilityStatus {
    FUNCTIONAL("Fonctionnel"),      // Aucune intervention active
    REPORTED("Signalé"),            // Intervention active, mais pas encore commencée (PENDING/SYNDIC_ASSIGNED/QUOTE_VALIDATED)
    IN_PROGRESS("En travaux");      // Intervention en cours (STARTED) ou terminée (FINISHED) mais pas encore validée (Final_validated)

    private final String label;

    CommonFacilityStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
