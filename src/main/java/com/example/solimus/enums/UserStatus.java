package com.example.solimus.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserStatus {
    PENDING("En attente"),
    ACTIVE("Actif"),
    DISABLED("Désactivé");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static UserStatus fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        for (UserStatus s : values()) {
            if (s.name().equalsIgnoreCase(v) || s.label.equalsIgnoreCase(v)) return s;
        }
        throw new IllegalArgumentException("Statut inconnu: " + value);
    }
}
