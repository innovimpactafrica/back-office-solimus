package com.example.solimus.enums;

public enum AdminNotificationEventType {
    NEW_SYNDIC_CREATED("Nouveau syndic créé", "Dès qu'un nouveau syndic est créé"),
    NEW_PROVIDER_REGISTERED("Nouveau prestataire inscrit", "À chaque inscription"),
    SUBSCRIPTION_EXPIRED("Abonnement expiré", "À chaque abonnement expiré"),
    PAYMENT_RECEIVED("Paiement reçu", "À chaque paiement reçu"),
    PAYMENT_FAILED("Paiement échoué", "À chaque paiement échoué"),
    NEW_DOCUMENT("Nouveau document", "Document ajouté à la plateforme");

    private final String label;
    private final String description;

    AdminNotificationEventType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}