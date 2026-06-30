package com.example.solimus.enums;

public enum PaymentMethod {
    WAVE("Wave"),
    ORANGE_MONEY("Orange Money"),
    CARTE_BANCAIRE("Carte bancaire");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
