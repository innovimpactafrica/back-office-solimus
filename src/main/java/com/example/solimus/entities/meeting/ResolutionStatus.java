package com.example.solimus.entities.meeting;

// Statut de résolution : purement informatif, saisi manuellement par le syndic après la réunion
// Aucun calcul automatique, aucune pondération par tantième
public enum ResolutionStatus {

    EN_ATTENTE("En attente"),   // valeur par defaut, point pas encore traite
    ADOPTEE("Adoptée"),         // marque comme adoptee par le syndic
    REJETEE("Rejetée"),         // marque comme rejetee par le syndic
    REPORTEE("Reportée");       // reportee a une prochaine reunion

    private final String description; // libelle francais pour l'affichage

    ResolutionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
