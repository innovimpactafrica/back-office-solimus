package com.example.solimus.enums;

public enum PropertyDisplayStatus {
    MAINTENANCE("En maintenance"),
    UNPAID("Impayé"),      // Correspond à PaymentDelayStatus.UNPAID
    LATE("Retard"),        // Correspond à PaymentDelayStatus.LATE
    OCCUPIED("Occupé"),
    VACANT("Vacant");

    private final String label;

    PropertyDisplayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
