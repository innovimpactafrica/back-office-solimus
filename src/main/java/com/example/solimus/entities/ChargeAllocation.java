package com.example.solimus.entities;

import com.example.solimus.enums.ChargeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité représentant la part individuelle d'un copropriétaire pour une charge.
 * 
 * Une allocation représente la part d'une charge que doit payer un copropriétaire
 * pour son bien (appartement, parking, etc.).
 * 
 * Le système crée automatiquement une allocation par bien de la résidence
 * lors de la création d'une charge, en répartissant le montant total
 * selon les parts de chaque copropriétaire.
 * 
 * Chaque allocation a son propre statut de paiement qui évolue indépendamment
 * des autres allocations de la même charge.
 * 
 * Relations JPA :
 * - ChargeAllocation : relation ManyToOne avec Charge (une allocation appartient à une charge)
 * - ChargeAllocation : relation ManyToOne avec Property (une allocation est liée à un bien)
 * - ChargeAllocation : relation ManyToOne avec User (une allocation est liée à un copropriétaire)
 */
@Entity
@Table(name = "charge_allocations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargeAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================================
    // IDENTIFICATION
    // =========================================================================

    /**
     * Référence unique de l'allocation (ex: CHG-2026-06-A12).
     * 
     * Cette référence est générée automatiquement lors de la création
     * et permet d'identifier l'allocation de manière unique dans le système.
     * Format : CHG-XXXXXX-AX (préfixe charge + nombre aléatoire + suffixe A + numéro)
     */
    @Column(unique = true, nullable = false)
    private String reference;

    // =========================================================================
    // RELATIONS JPA
    // =========================================================================

    /**
     * La charge parente à laquelle cette allocation appartient.
     * 
     * Relation ManyToOne : une allocation appartient à une seule charge,
     * mais une charge peut avoir plusieurs allocations (une par bien).
     * 
     * FetchType.LAZY : on charge la charge uniquement quand nécessaire
     * pour optimiser les performances.
     * 
     * Cette relation permet de regrouper les allocations sous une charge
     * et de garantir l'intégrité référentielle (suppression en cascade).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    /**
     * Le bien concerné par cette allocation.
     * 
     * Relation ManyToOne : une allocation est liée à un seul bien,
     * mais un bien peut avoir plusieurs allocations (une par charge).
     * 
     * FetchType.LAZY : on charge le bien uniquement quand nécessaire.
     * 
     * Cette relation permet de savoir quel bien est concerné par cette part
     * et de calculer la répartition selon la surface ou les quotes-parts.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    /**
     * Le copropriétaire qui doit payer cette allocation.
     * 
     * Relation ManyToOne : une allocation est liée à un seul copropriétaire,
     * mais un copropriétaire peut avoir plusieurs allocations (une par charge).
     * 
     * FetchType.LAZY : on charge le copropriétaire uniquement quand nécessaire.
     * 
     * Cette relation permet d'identifier le responsable du paiement
     * et d'envoyer les notifications appropriées.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // =========================================================================
    // MONTANT ET STATUT
    // =========================================================================

    /**
     * Montant dû par ce copropriétaire pour cette charge.
     * 
     * Ce montant est calculé automatiquement lors de la création de l'allocation
     * en fonction de la part du bien dans la résidence (surface, quotes-parts, etc.).
     * 
     * La somme de tous les montants d'allocation d'une charge doit correspondre
     * au totalAmount de la charge parente.
     */
    @Column(nullable = false)
    private BigDecimal amount;

    /**
     * Statut de paiement de cette allocation.
     * 
     * EN_ATTENTE : l'allocation a été créée mais n'a pas encore été payée
     * PAYEE : le copropriétaire a payé sa part
     * EN_RETARD : la date d'échéance est dépassée et le paiement n'a pas été effectué
     * 
     * Le statut évolue indépendamment des autres allocations de la même charge.
     * Un job planifié peut passer automatiquement les allocations en EN_RETARD
     * après la date d'échéance de la charge parente.
     */
    @Enumerated(EnumType.STRING)
    private ChargeStatus status;

    // =========================================================================
    // AUDIT
    // =========================================================================

    /**
     * Date de création de l'allocation.
     * 
     * @CreationTimestamp : automatiquement renseignée par Hibernate
     * lors de la première insertion en base.
     * updatable = false : cette date ne change jamais après création.
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
