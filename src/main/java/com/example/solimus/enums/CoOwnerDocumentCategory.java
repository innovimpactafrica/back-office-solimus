package com.example.solimus.enums;

// Categorie d'un document rattache a un coproprietaire
public enum CoOwnerDocumentCategory {

    PROPERTY_TITLE("Titre de propriété"),
    CONTRACT("Contrats"),
    IDENTITY_DOCUMENT("Pièces d'identité"),
    PAYMENT_RECEIPT("Reçus de paiement"),
    MEETING_MINUTES("PV d'assemblée");

    private final String description; // libelle francais pour l'affichage

    CoOwnerDocumentCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description; // pour l'affichage uniquement, jamais pour le stockage
    }
}