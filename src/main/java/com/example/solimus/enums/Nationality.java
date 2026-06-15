package com.example.solimus.enums;

// =============================================================================
//
//  NATIONALITY — Nationalité du copropriétaire
//
//  Liste des nationalités courantes avec label en français pour l'affichage.
//
// =============================================================================
public enum Nationality {

    /** Française */
    FRANCAISE("Française"),

    /** Marocaine */
    MAROCAINE("Marocaine"),

    /** Algérienne */
    ALGERIENNE("Algérienne"),

    /** Tunisienne */
    TUNISIENNE("Tunisienne"),

    /** Sénégalaise */
    SENEGALAISE("Sénégalaise"),

    /** Ivoirienne */
    IVOIRIENNE("Ivoirienne"),

    /** Camerounaise */
    CAMEROUNAISE("Camerounaise"),

    /** Béninoise */
    BENINOISE("Béninoise"),

    /** Burkinabè */
    BURKINABE("Burkinabè"),

    /** Malienne */
    MALIENNE("Malienne"),

    /** Nigérienne */
    NIGERIENNE("Nigérienne"),

    /** Togolaise */
    TOGOLAISE("Togolaise"),

    /** Guinéenne */
    GUINEENNE("Guinéenne"),

    /** Congolaise (RDC) */
    CONGOLAISE_RDC("Congolaise (RDC)"),

    /** Congolaise (Congo) */
    CONGOLAISE("Congolaise"),

    /** Gabonaise */
    GABONAISE("Gabonaise"),

    /** Centrafricaine */
    CENTRAFRICAINE("Centrafricaine"),

    /** Tchadienne */
    TCHADIENNE("Tchadienne"),

    /** Mauritanienne */
    MAURITANIENNE("Mauritanienne"),

    /** Libyenne */
    LIBYENNE("Libyenne"),

    /** Égyptienne */
    EGYPTIENNE("Égyptienne"),

    /** Belge */
    BELGE("Belge"),

    /** Suisse */
    SUISSE("Suisse"),

    /** Canadienne */
    CANADIENNE("Canadienne"),

    /** Américaine */
    AMERICAINE("Américaine"),

    /** Britannique */
    BRITANNIQUE("Britannique"),

    /** Allemande */
    ALLEMANDE("Allemande"),

    /** Espagnole */
    ESPAGNOLE("Espagnole"),

    /** Italienne */
    ITALIENNE("Italienne"),

    /** Portugaise */
    PORTUGAISE("Portugaise"),

    /** Autre */
    AUTRE("Autre");

    private final String label;

    Nationality(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
