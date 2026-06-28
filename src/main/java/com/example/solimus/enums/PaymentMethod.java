package com.example.solimus.enums;

public enum PaymentMethod {
    WAVE("Wave"),
    ORANGE_MONEY("Orange Money"),
    VIREMENT_BANCAIRE("Virement Bancaire");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
