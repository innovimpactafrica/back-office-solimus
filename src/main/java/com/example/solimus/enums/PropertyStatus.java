package com.example.solimus.enums;

// =============================================================================
//  PROPERTY STATUS — Statut d'un lot
// =============================================================================
public enum PropertyStatus {

    OCCUPIED("Occupé"),
    VACANT("Vacant"),
    MAINTENANCE("En maintenance");

    private final String label;

    PropertyStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
