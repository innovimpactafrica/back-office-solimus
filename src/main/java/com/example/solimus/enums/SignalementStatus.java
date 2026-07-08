package com.example.solimus.enums;

/**
 * Statuts possibles d'un signalement dans le workflow Solimus.
 */
public enum SignalementStatus {
    PENDING("En attente"),
    IN_PROGRESS("En cours"),
    RESOLVED("Traité"),
    CONVERTED_TO_WORK("En Travaux");

    private final String label;

    SignalementStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}