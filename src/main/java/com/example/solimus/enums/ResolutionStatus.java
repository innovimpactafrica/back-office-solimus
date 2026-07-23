package com.example.solimus.enums;

// Statut de résolution : purement informatif, saisi manuellement par le syndic après la réunion
// Aucun calcul automatique, aucune pondération par tantième
public enum ResolutionStatus {

    EN_ATTENTE("En attente"),   // valeur par defaut, point pas encore traite
    ADOPTEE("Adoptée"),         // marque comme adoptée par le syndic
    REJETEE("Rejetée"),         // marque comme rejetée par le syndic
    REPORTEE("Reportée");       // reportée a une prochaine réunion

    private final String description; // libelle francais pour l'affichage

    ResolutionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
