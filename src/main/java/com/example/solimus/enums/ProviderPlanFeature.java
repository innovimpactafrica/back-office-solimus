package com.example.solimus.enums;

// Fonctionnalité activable sur une formule d'abonnement prestataire
public enum ProviderPlanFeature {

    FULL_ACCESS("Accès à toutes les fonctionnalités");

    private final String label;

    ProviderPlanFeature(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
