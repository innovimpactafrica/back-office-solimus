package com.example.solimus.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ERole {
    ROLE_ADMIN("Administrateur"),
    ROLE_SYNDIC("Syndic"),
    ROLE_PRESTATAIRE("Prestataire"),
    ROLE_COPROPRIETAIRE("Copropriétaire");

    private final String label;

    ERole(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ERole fromString(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        for (ERole r : values()) {
            if (r.name().equalsIgnoreCase(v) || r.label.equalsIgnoreCase(v)) return r;
        }
        throw new IllegalArgumentException("Rôle inconnu: " + value);
    }
}
