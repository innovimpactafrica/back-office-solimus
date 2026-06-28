package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.FuelType;
import com.example.solimus.enums.GardenState;
import com.example.solimus.enums.PumpStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =============================================================================
//  ADD FACILITY DTO — Étape 3
//
//  Représente un équipement commun à ajouter dans une résidence.
//  Remplissage OPTIONNEL.
//
//  Un seul appel suffit pour tous les équipements.
//  Les champs non pertinents pour un type d'équipement sont ignorés.
//
//  Exemple pour une PISCINE :
//  { "facilityType": "PISCINE", "count": 2, "isHeated": true }
//
//  Exemple pour un PARKING :
//  { "facilityType": "PARKING", "indoorSpots": 30,
//    "outdoorSpots": 20, "chargingStations": 5 }
//
// =============================================================================
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddFacilityDTO {

    // -------------------------------------------------------------------------
    // TYPE D'ÉQUIPEMENT
    // -------------------------------------------------------------------------

    /**
     * ID du type d'équipement commun (référence vers FacilityType).
     */
    @NotNull(message = "Le type d'équipement est obligatoire")
    private Long facilityTypeId;


    // -------------------------------------------------------------------------
    // CHAMPS COMMUNS — PISCINE / ASCENSEUR / COULOIR
    // -------------------------------------------------------------------------

    /** Nombre d'unités. Exemple : 2 piscines, 3 ascenseurs, 4 couloirs */
    private Integer count;


    // -------------------------------------------------------------------------
    // CHAMPS PISCINE
    // -------------------------------------------------------------------------

    /** La piscine est-elle chauffée ? true / false */
    private Boolean isHeated;


    // -------------------------------------------------------------------------
    // CHAMPS ASCENSEUR
    // -------------------------------------------------------------------------

    /** Capacité en nombre de personnes. Exemple : 8 */
    private Integer capacity;


    // -------------------------------------------------------------------------
    // CHAMPS COULOIR
    // -------------------------------------------------------------------------

    /** Nombre d'étages couverts par les couloirs. Exemple : 3 */
    private Integer floorsCovered;


    // -------------------------------------------------------------------------
    // CHAMPS JARDIN
    // -------------------------------------------------------------------------

    /** Superficie en m². Exemple : 100 */
    private Integer superficie;

    /** État du jardin */
    private GardenState etat;


    // -------------------------------------------------------------------------
    // CHAMPS PARKING & JARDINS/ESPACES VERTS
    // -------------------------------------------------------------------------

    /** Places de parking intérieures. Exemple : 30 */
    private Integer indoorSpots;

    /** Places de parking extérieures. Exemple : 20 */
    private Integer outdoorSpots;

    /** Bornes de recharge électrique. Exemple : 5 */
    private Integer chargingStations;


    // -------------------------------------------------------------------------
    // CHAMPS GROUPE ÉLECTROGÈNE
    // -------------------------------------------------------------------------

    /** Puissance en KVA. Exemple : 30 */
    private Integer powerKva;

    /** Type de carburant */
    private FuelType fuelType;


    // -------------------------------------------------------------------------
    // CHAMPS RÉSERVOIR D'EAU
    // -------------------------------------------------------------------------

    /** Capacité en litres. Exemple : 500 */
    private Integer capacityLiters;

    /** Statut de la pompe */
    private PumpStatus pumpStatus;
}
