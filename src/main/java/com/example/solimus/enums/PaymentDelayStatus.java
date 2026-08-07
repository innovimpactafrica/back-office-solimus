package com.example.solimus.enums;

public enum PaymentDelayStatus {
    UP_TO_DATE("À jour"),
    LATE("Retard"),
    UNPAID("Impayé");

    private final String label;

    PaymentDelayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
