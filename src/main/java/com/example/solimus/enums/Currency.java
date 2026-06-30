package com.example.solimus.enums;

/**
 * Devise monétaire utilisée pour les transactions financières de la copropriété.
 */
public enum Currency {
    FCFA("FCFA"),
    EUR("Euro"),
    USD("Dollar");

    private final String label;

    Currency(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
