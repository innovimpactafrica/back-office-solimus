package com.example.solimus.enums;

// Statut de location d'un lot — spécifique à l'onglet "Appartements" de la fiche résidence.
public enum PropertyRentalStatus {
    VACANT("Vacant"),           // Pas de copropriétaire, pas de locataire
    RENTED("Loué"),             // Copropriétaire ET locataire présents
    TO_RENT("À louer");         // Copropriétaire présent, pas de locataire

    private final String label;

    PropertyRentalStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
