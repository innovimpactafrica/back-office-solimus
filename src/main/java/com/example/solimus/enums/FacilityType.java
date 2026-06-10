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
    PISCINE,

    /** Ascenseur — nombre + capacité personnes */
    ASCENSEUR,

    /** Couloir — nombre + étages concernés */
    COULOIR,

    /** Jardin — superficie + état */
    JARDIN,

    /** Parking & stationnement — places intérieures/extérieures + bornes recharge */
    PARKING,

    /** Jardins & espaces verts — mêmes champs que PARKING */
    JARDINS_ESPACES_VERTS,

    /** Groupe électrogène — puissance KVA + type carburant */
    GROUPE_ELECTROGENE,

    /** Réservoir d'eau — capacité litres + pompe de relevage */
    RESERVOIR_EAU
}