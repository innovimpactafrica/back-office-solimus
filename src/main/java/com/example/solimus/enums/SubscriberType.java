package com.example.solimus.enums;

// Type d'abonné : distingue un syndic d'un prestataire dans les listes unifiées
public enum SubscriberType {

    SYNDIC("Syndic"),
    PRESTATAIRE("Prestataire");

    private final String label;

    SubscriberType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
