package com.example.solimus.enums;

public enum FacilityCategory {
    EQUIPMENT("Équipement"),      // Équipement
    LEISURE("Loisirs"),           // Loisirs
    SECURITY("Sécurité"),         // Sécurité
    INFRASTRUCTURE("Infrastructure"), // Infrastructure
    ACCESS("Accès");              // Accès

    private final String label;

    FacilityCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
