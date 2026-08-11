package com.example.solimus.enums;

import lombok.Getter;

/**
 * Fréquence des appels de charges pour une copropriété.
 */
@Getter
public enum ChargeFrequency {
    MENSUEL("Mensuel"),
    TRIMESTRIEL("Trimestriel");

    private final String label;

    ChargeFrequency(String label) {
        this.label = label;
    }

}
