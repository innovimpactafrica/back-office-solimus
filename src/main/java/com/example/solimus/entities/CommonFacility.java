package com.example.solimus.entities;

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
//  Représente un équipement partagé par tous les copropriétaires
//  (piscine, ascenseur, parking, groupe électrogène...).
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
    // =========================================================================

    /**
     * La résidence à laquelle appartient cet équipement.
     * plusieurs équipements peuvent être dans une résidence et avoir le même type
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;


    // =========================================================================
    // DÉTAILS
    // =========================================================================

    /**
     * Détails saisis librement par le syndic pour cette instance précise
     * (ex: "3 piscines chauffées, capacité 50 personnes chacune").
     * Remplace tous les anciens champs structurés (count, capacity, etat, etc.).
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;


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