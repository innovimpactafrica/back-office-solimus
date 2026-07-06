package com.example.solimus.enums;

public enum SignalementStatus {
    PENDING("En attente"),
    IN_TRAVAUX("En travaux"),
    RESOLVED("Traité");

    private final String label;

    SignalementStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}