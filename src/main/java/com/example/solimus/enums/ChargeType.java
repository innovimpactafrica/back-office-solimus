package com.example.solimus.enums;

// Types de charges pour une résidence
public enum ChargeType {
    REGULAR("Charge Courante"),
    EXCEPTIONAL("Charge Exceptionnelle");

    private final String description;

    ChargeType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
