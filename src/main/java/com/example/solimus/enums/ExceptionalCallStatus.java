package com.example.solimus.enums;

public enum ExceptionalCallStatus {
    DRAFT("Brouillon"),
    ACTIVE("En cours"),
    CLOSED("Clôturé");

    private final String label;

    ExceptionalCallStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
