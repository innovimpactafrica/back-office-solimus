package com.example.solimus.enums;

//Niveau d'urgence de la demande de travaux
public enum UrgencyLevel {
    FAIBLE("Faible"),
    MOYEN("Moyen"),
    URGENT("Urgent");

    private final String label;

    UrgencyLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
