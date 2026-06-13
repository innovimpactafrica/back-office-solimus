package com.example.solimus.enums;

/**
 * Type de bien immobilier géré dans le système.
 */
public enum PropertyType {
    APPARTEMENT("Appartement"),
    STUDIO("Studio"),
    LOCAL_COMMERCIAL("Local commercial"),
    PARKING("Parking");

    private final String label;

    PropertyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
