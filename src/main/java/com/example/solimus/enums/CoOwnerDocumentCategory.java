package com.example.solimus.enums;

// Catégorie d'un document rattaché a un coproprietaire
public enum CoOwnerDocumentCategory {

    PROPERTY_TITLE("Titre de propriété"),
    CONTRACT("Contrats"),
    IDENTITY_DOCUMENT("Pièces d'identité"),
    PAYMENT_RECEIPT("Reçus de paiement");

    private final String description; // libellé francais pour l'affichage

    CoOwnerDocumentCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description; }
}