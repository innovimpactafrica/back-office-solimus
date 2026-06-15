package com.example.solimus.enums;

// =============================================================================
//
//  TITLE — Civilité du copropriétaire
//
//  Affiché sous forme de boutons radio dans le formulaire
//  de création d'un copropriétaire.
//
// =============================================================================
public enum Title {

    /** Monsieur */
    MR("Monsieur"),

    /** Madame */
    MRS("Madame"),

    /** Société */
    COMPANY("Société");

    private final String label;

    Title(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
