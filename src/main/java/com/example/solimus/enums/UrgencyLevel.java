package com.example.solimus.enums;

public enum UrgencyLevel {
    FAIBLE("Faible"),
    MOYEN("Moyen"),
    URGENT("Urgent"),
    CRITIQUE("Critique");

    private final String label;

    UrgencyLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
