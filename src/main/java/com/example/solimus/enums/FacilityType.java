package com.example.solimus.enums;

// =============================================================================
//
//  FACILITY TYPE — Type d'équipement commun
//
//  Correspond aux sections de l'Étape 3 du formulaire
//  "Biens communs" lors de la création d'une résidence.
//
// =============================================================================
public enum FacilityType {

    /** Piscine — nombre + chauffée ou non */
    PISCINE("Piscine"),

    /** Ascenseur — nombre + capacité personnes */
    ASCENSEUR("Ascenseur"),

    /** Couloir — nombre + étages concernés */
    COULOIR("Couloir"),

    /** Jardin — superficie + état */
    JARDIN("Jardin"),

    /** Parking & stationnement — places intérieures/extérieures + bornes recharge */
    PARKING("Parking"),

    /** Jardins & espaces verts — mêmes champs que PARKING */
    JARDINS_ESPACES_VERTS("Jardins et espaces verts"),

    /** Groupe électrogène — puissance KVA + type carburant */
    GROUPE_ELECTROGENE("Groupe électrogène"),

    /** Réservoir d'eau — capacité litres + pompe de relevage */
    RESERVOIR_EAU("Réservoir d'eau");

    private final String label;

    FacilityType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}