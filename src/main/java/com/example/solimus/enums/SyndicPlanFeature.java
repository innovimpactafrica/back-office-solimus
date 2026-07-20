package com.example.solimus.enums;

// Fonctionnalité activable sur une formule d'abonnement syndic
public enum SyndicPlanFeature {

    RESIDENCE_MANAGEMENT("Gestion des résidences"),
    COOWNER_MANAGEMENT("Gestion des copropriétaires"),
    INCIDENT_MANAGEMENT("Gestion des incidents"),
    AG_MANAGEMENT("Gestion des AG"),
    DOCUMENT_MANAGEMENT("Gestion documentaire"),
    PRIORITY_SUPPORT("Support prioritaire"),
    ADVANCED_STATS("Statistiques avancées"),
    UNLIMITED_USERS("Utilisateurs illimités");

    private final String label;

    SyndicPlanFeature(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}