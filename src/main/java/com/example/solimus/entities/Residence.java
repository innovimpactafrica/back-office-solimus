package com.example.solimus.entities;

import com.example.solimus.enums.ResidenceHealthStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// =============================================================================
//
//  RESIDENCE
//
//  Représente une copropriété ou un immeuble géré par un syndic.
//
//  Une résidence contient :
//  → Des lots (Property)
//  → Des équipements communs (CommonFacility)
//  → Des contacts clés (ResidenceContact)
//  → Des demandes d'intervention (InterventionRequest)
//
//  Création en 3 étapes depuis le dashboard syndic :
//  Étape 1 → Informations générales
//  Étape 2 → Appartements
//  Étape 3 → Biens communs (optionnel)
//
// =============================================================================
@Entity
@Table(name = "residences")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Residence {


    // =========================================================================
    // IDENTIFIANT
    // =========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================================
    // ÉTAPE 1 — INFORMATIONS GÉNÉRALES
    // =========================================================================

    /**
     * Nom affiché de la résidence.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Description générale de la résidence.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Photo principale de la résidence.
     */
    @Column(name = "photo_url")
    private String photoUrl;


    // =========================================================================
    // ÉTAPE 1 — LOCALISATION
    // =========================================================================

    /**
     * Adresse complète.
     */
    @Column(name = "full_address", nullable = false, length = 500)
    private String fullAddress;

    /**
     * Ville de la résidence.
     */
    private String city;

    /**
     * Pays de la résidence.
     */
    private String country;

    /**
     * Latitude GPS de la résidence.
     * Utilisée pour :
     * → Afficher la carte dans le dashboard
     * → Rechercher les prestataires dans un rayon de 30km
     */
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    /**
     * Longitude GPS de la résidence.
     */
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;


    // =========================================================================
    // ÉTAPE 1 — CARACTÉRISTIQUES DU BÂTIMENT
    // =========================================================================


    /**
     * Nombre total de lots dans la résidence.
     * Un lot = appartement, studio, parking, local commercial.
     * Mis à jour automatiquement à chaque ajout/suppression de Property.
     */
    @Column(name = "lots_count")
    private Integer lotsCount;

    /**
     * Date de construction du bâtiment.
     */
    @Column(name = "construction_date")
    private LocalDate constructionDate;

    /**
     * Date de la dernière rénovation connue.
     */
    @Column(name = "renovation_date")
    private LocalDate renovationDate;


    // =========================================================================
    // INFORMATIONS FINANCIÈRES
    // =========================================================================

    /**
     * Budget annuel de fonctionnement de la résidence en FCFA.
     */
    @Column(name = "annual_budget", precision = 15, scale = 2)
    private BigDecimal annualBudget;

    /**
     * Indicateur global de santé financière de la résidence.
     * Calculé automatiquement à partir des impayés et incidents.
     * Valeurs : BONNE, MOYENNE, CRITIQUE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "health_status")
    private ResidenceHealthStatus healthStatus;


    // =========================================================================
    // SYNDIC GESTIONNAIRE
    // =========================================================================

    /**
     * Syndic responsable de la gestion de cette résidence.
     * Un syndic peut gérer plusieurs résidences.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syndic_id", nullable = false)
    private User syndic;


    // =========================================================================
    // ÉTAPE 2 — LOTS
    // =========================================================================

    /**
     * Liste de tous les biens (lots) de la résidence.
     */
    @OneToMany(
            mappedBy = "residence",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Property> properties = new ArrayList<>();


    // =========================================================================
    // ÉTAPE 3 — ÉQUIPEMENTS COMMUNS (optionnel) / SÉCURITÉ & ACCÈS
    // =========================================================================

    /**
     * Équipements partagés par tous les copropriétaires.
     */
    @OneToMany(
            mappedBy = "residence",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CommonFacility> commonFacilities = new ArrayList<>();

    /**
     * Options de sécurité actives dans cette résidence.
     * Choisies par le syndic parmi les options créées par l'admin.
     */
    @ManyToMany
    @JoinTable(
            name = "residence_security_features",
            joinColumns = @JoinColumn(name = "residence_id"),
            inverseJoinColumns = @JoinColumn(name = "security_feature_id")
    )
    private List<SecurityFeature> securityFeatures = new ArrayList<>();


    // =========================================================================
    // CONTACTS CLÉS
    // =========================================================================

    /**
     * Personnes à contacter pour la résidence.
     */
    @OneToMany(
            mappedBy = "residence",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ResidenceContact> contacts = new ArrayList<>();

    // =========================================================================
    // INTERVENTIONS
    // =========================================================================

    /**
     * Historique de toutes les demandes d'intervention liées à la résidence.
     */
    @OneToMany(mappedBy = "residence", cascade = CascadeType.ALL)
    private List<InterventionRequest> interventionRequests = new ArrayList<>();


    // =========================================================================
    // AUDIT — DATES AUTOMATIQUES
    // =========================================================================

    /**
     * Date de création de la résidence dans le système.
     * Remplie automatiquement par Hibernate à l'insertion.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Date de la dernière modification.
     * Mise à jour automatiquement par Hibernate à chaque save().
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}