package com.example.solimus.entities;

import com.example.solimus.enums.GardenState;
import com.example.solimus.enums.FuelType;
import com.example.solimus.enums.PumpStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// =============================================================================
//
//  COMMON FACILITY — Équipement commun d'une résidence
//
//  Représente un équipement partagé par tous les copropriétaires.
//
//  Types d'équipements gérés (Étape 3 du formulaire) :
//  → PISCINE              : nombre, chauffée ou non
//  → ASCENSEUR            : nombre, capacité personnes
//  → COULOIR              : nombre, étages concernés
//  → JARDIN               : superficie, état
//  → PARKING              : places intérieures, extérieures, bornes recharge
//  → JARDINS_ESPACES_VERTS: mêmes champs que PARKING
//  → GROUPE_ELECTROGENE   : puissance KVA, type carburant
//  → RESERVOIR_EAU        : capacité litres, pompe de relevage
//
//  Remplissage OPTIONNEL — peut être ajouté/modifié après création
//  de la résidence.
//
// =============================================================================
@Entity
@Table(name = "common_facilities")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommonFacility {


    // =========================================================================
    // IDENTIFIANT
    // =========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================================
    // TYPE D'ÉQUIPEMENT
    // =========================================================================

    /**
     * Type de l'équipement commun (référence vers la table des types prédéfinis).
     * Plusieurs équipements peuvent avoir le même type
     *
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "facility_type_id", nullable = false)
    private FacilityType facilityType;


    // =========================================================================
    // RÉSIDENCE PARENTE
    // ========================================= ================================

    /**
     * La résidence à laquelle appartient cet équipement.
     * plusieurs équipements peuvent être dans une résidence et avoir le même type
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;


    // =========================================================================
    // CHAMPS COMMUNS — PISCINE & ASCENSEUR
    // =========================================================================

    /**
     * Nombre d'unités de cet équipement.
     *
     * Utilisé pour :
     * → PISCINE    : nombre de piscines (ex: 2)
     * → ASCENSEUR  : nombre d'ascenseurs (ex: 3)
     * → COULOIR    : nombre de couloirs (ex: 4)
     */
    @Column(name = "count")
    private Integer count;


    // =========================================================================
    // CHAMPS PISCINE
    // =========================================================================

    /**
     * Indique si la piscine est chauffée.
     * true  → Piscine chauffée
     * false → Piscine non chauffée
     * null  → Non applicable (pas une piscine)
     */
    @Column(name = "is_heated")
    private Boolean isHeated;


    // =========================================================================
    // CHAMPS ASCENSEUR
    // =========================================================================

    /**
     * Capacité maximale de l'ascenseur en nombre de personnes.
     * Exemple : 8 personnes, 13 personnes
     * null → Non applicable
     */
    @Column(name = "capacity")
    private Integer capacity;


    // =========================================================================
    // CHAMPS COULOIR
    // =========================================================================

    /**
     * Nombre d'étages couverts par les couloirs.
     * Exemple : 3 étages
     * null → Non applicable
     */
    @Column(name = "floors_covered")
    private Integer floorsCovered;


    // =========================================================================
    // CHAMPS JARDIN
    // =========================================================================

    /**
     * Superficie du jardin en mètres carrés.
     * Exemple : 100 m²
     * null → Non applicable
     */
    @Column(name = "superficie")
    private Integer superficie;

    /**
     * État actuel du jardin.
     * null → Non applicable
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "etat")
    private GardenState etat;


    // =========================================================================
    // CHAMPS PARKING & JARDINS/ESPACES VERTS
    // =========================================================================

    /**
     * Nombre de places de parking intérieures.
     * Exemple : 30 places
     * null → Non applicable
     */
    @Column(name = "indoor_spots")
    private Integer indoorSpots;

    /**
     * Nombre de places de parking extérieures.
     * Exemple : 20 places
     * null → Non applicable
     */
    @Column(name = "outdoor_spots")
    private Integer outdoorSpots;

    /**
     * Nombre de bornes de recharge électrique.
     * Exemple : 5 bornes
     * null → Non applicable
     */
    @Column(name = "charging_stations")
    private Integer chargingStations;


    // =========================================================================
    // CHAMPS GROUPE ÉLECTROGÈNE
    // =========================================================================

    /**
     * Puissance du groupe électrogène en KVA.
     * Exemple : 30 KVA
     * null → Non applicable
     */
    @Column(name = "power_kva")
    private Integer powerKva;

    /**
     * Type de carburant utilisé par le groupe électrogène.
     * null → Non applicable
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type")
    private FuelType fuelType;


    // =========================================================================
    // CHAMPS RÉSERVOIR D'EAU
    // =========================================================================

    /**
     * Capacité du réservoir d'eau en litres.
     * Exemple : 500 litres, 5000 litres
     * null → Non applicable
     */
    @Column(name = "capacity_liters")
    private Integer capacityLiters;

    /**
     * Indique si une pompe de relevage est installée.
     * null → Non applicable
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pump_status")
    private PumpStatus pumpStatus;


    // =========================================================================
    // AUDIT — DATES AUTOMATIQUES
    // =========================================================================

    /**
     * Date d'ajout de l'équipement dans le système.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Date de dernière modification.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}