package com.example.solimus.enums;

/**
 * Fréquence des appels de charges pour une copropriété.
 */
public enum ChargeFrequency {
    MENSUEL("Mensuel"),
    TRIMESTRIEL("Trimestriel");

    private final String label;

    ChargeFrequency(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
