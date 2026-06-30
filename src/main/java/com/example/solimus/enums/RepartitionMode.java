package com.example.solimus.enums;

/**
 * Mode de répartition des charges entre copropriétaires.
 */
public enum RepartitionMode {
    OWNERSHIP_SHARES("Tantièmes");
    // ÉGALITAIRE et AU_M2 seront ajoutés plus tard

    private final String label;

    RepartitionMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
