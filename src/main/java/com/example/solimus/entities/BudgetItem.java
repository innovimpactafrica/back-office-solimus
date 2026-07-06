package com.example.solimus.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ============================================================================
 * POSTE BUDGÉTAIRE
 * ============================================================================
 * Ligne saisie librement par le syndic (ex: "Entretien parties communes").
 */
@Entity
@Table(name = "budget_items")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BudgetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Column(nullable = false)
    private String libelle; // Ex: "Entretien parties communes", "Assurances"

    @Column(nullable = false)
    private BigDecimal montant;

    /**
     * Équipement commun de la résidence auquel ce poste budgétaire est éventuellement rattaché.
     * Nullable — rempli uniquement si le syndic a sélectionné une suggestion d'autocomplétion.
     * Si null, le poste reste un texte libre (libelle), sans lien avec un équipement précis.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "common_facility_id")
    private CommonFacility commonFacility;
}
