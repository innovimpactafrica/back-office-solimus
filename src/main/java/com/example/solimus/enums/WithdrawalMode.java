package com.example.solimus.enums;

/**
 * Mode de retrait de fonds du portefeuille syndic
 * Détermine comment le syndic souhaite recevoir ses fonds
 */
public enum WithdrawalMode {
    WAVE("Wave"),
    ORANGE_MONEY("Orange Money"),
    VIREMENT("Virement bancaire");
    private final String label;

    WithdrawalMode (String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}