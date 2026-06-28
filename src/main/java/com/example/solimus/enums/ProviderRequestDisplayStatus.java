package com.example.solimus.enums;

// Statut tel qu'il est affiché à CE prestataire précis pour une demande donnée
// — différent du InterventionStatus global de la demande
public enum ProviderRequestDisplayStatus {

    PENDING_QUOTE("En attente devis"),
    QUOTE_SENT("Devis envoyé"),
    REJECTED("Refusé");

    private final String label;

    ProviderRequestDisplayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}