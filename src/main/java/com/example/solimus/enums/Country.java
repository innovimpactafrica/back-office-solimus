package com.example.solimus.enums;

// Pays disponibles pour les informations société d'un syndic
public enum Country {

    SENEGAL("Sénégal"),
    COTE_DIVOIRE("Côte d'Ivoire"),
    TOGO("Togo"),
    MALI("Mali"),
    BENIN("Bénin"),
    BURKINA_FASO("Burkina Faso"),
    NIGER("Niger"),
    GUINEE("Guinée"),
    CAMEROUN("Cameroun"),
    FRANCE("France");

    private final String label;

    Country(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}