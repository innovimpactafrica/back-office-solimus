package com.example.solimus.entities;

import com.example.solimus.enums.PropertyStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// =============================================================================
//
//  PROPERTY : Représente un bien individuel dans une résidence.
//  Un bien peut être :
//  → Un appartement (T2, T3, T4...)
//  → Un studio
//  → Un local commercial
//  → Un parking
//  → Une cave
//
//  Créé dans l'Étape 2 du formulaire d'ajout de résidence.
//
// =============================================================================
@Entity
@Table(name = "properties")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Property {


    // =========================================================================
    // IDENTIFIANT
    // =========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================================================
    // IDENTIFICATION DU LOT
    // =========================================================================

    /**
     * Numéro unique du lot dans la résidence.
     * Exemple : "A103", "B204", "C22"
     */
    @Column(nullable = false)
    private String reference;

    /**
     * Bloc ou bâtiment auquel appartient ce lot.
     * Exemple : "Bloc A", "Bloc B", "Bâtiment Principal"
     */
    @Column(name = "bloc")
    private String bloc;

    /**
     * Étage du lot.
     * 0 = rez-de-chaussée, les étages positifs montent normalement.
     * La conversion d'affichage ("RDC" au lieu de "0") est gérée côté front.
     */
    @Column(name = "floor")
    private Integer floor;

    /**
     * Type de bien.
     * Référence vers l'entité PropertyType gérée par le syndic.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_type_id", nullable = false)
    private PropertyType typeBien;


    // =========================================================================
    // CARACTÉRISTIQUES PHYSIQUES
    // =========================================================================

    /**
     * Superficie du lot en mètres carrés.
     * Exemple : 85.0 → affiché "85 m²"
     */
    @Column(name = "superficie")
    private BigDecimal superficie;

    /**
     * Tantième du lot.
     * Représente la quote-part de ce lot dans les charges communes.
     * Exemple : 1.25 → ce lot paie 1.25% des charges totales de la résidence.
     * Utilisé pour le calcul automatique des allocations de charges.
     */
    @Column(name = "tantieme", precision = 5, scale = 2)
    private BigDecimal tantieme;


    // =========================================================================
    // STATUT DU LOT
    // =========================================================================

    /**
     * Statut actuel du lot.
     *
     * Valeurs :
     * → OCCUPE      : un résident habite le lot (badge vert)
     * → VACANT      : le lot est inoccupé (badge gris)
     * → MAINTENANCE : le lot est en travaux (badge orange)
     *
     * Affiché dans la colonne "STATUT" du tableau Appartements.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PropertyStatus status;


    // =========================================================================
    // PROPRIÉTAIRE DU LOT
    // =========================================================================

    /**
     * Copropriétaire propriétaire de ce lot.
     * Peut être null si le lot est VACANT (pas encore attribué).
     *
     * Affiché dans la colonne "PROPRIÉTAIRE" du tableau Appartements.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = true)
    private User owner;


    // =========================================================================
    // RÉSIDENCE PARENTE
    // =========================================================================

    /**
     * La résidence à laquelle appartient ce lot.
     * Relation obligatoire — un lot appartient toujours à une résidence.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "residence_id", nullable = false)
    private Residence residence;


    // =========================================================================
    // AUDIT — DATES AUTOMATIQUES
    // =========================================================================

    /**
     * Date de création du lot dans le système.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Date de dernière modification du lot.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
