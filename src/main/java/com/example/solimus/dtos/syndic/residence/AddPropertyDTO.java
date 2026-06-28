package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.PropertyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// =============================================================================
//
//  ADD PROPERTY DTO — Étape 2
//
//  Représente un lot à ajouter dans une résidence.
//  Appelé autant de fois que nécessaire (un appel par lot).
//
//  Le copropriétaire peut être assigné immédiatement (ownerId)
//  ou laissé vacant (ownerId = null).
//
// =============================================================================
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddPropertyDTO {

    // -------------------------------------------------------------------------
    // IDENTIFICATION DU LOT
    // -------------------------------------------------------------------------

    /**
     * Numéro du lot.
     * Exemple : "A103", "B204", "C22"
     */
    @NotBlank(message = "Le numéro de lot est obligatoire")
    private String reference;

    /**
     * Bloc ou bâtiment.
     * Exemple : "Bloc A", "Bloc B"
     */
    private String bloc;

    /**
     * Étage du lot.
     * Exemple : "1", "4ème", "RDC"
     */
    private String floor;

    /**
     * Type de bien.
     * ID du type de bien géré par le syndic.
     */
    @NotNull(message = "Le type de bien est obligatoire")
    private Long propertyTypeId;


    // -------------------------------------------------------------------------
    // CARACTÉRISTIQUES PHYSIQUES
    // -------------------------------------------------------------------------

    /**
     * Superficie en m².
     */
    private BigDecimal superficie;

    /**
     * Tantième du lot.
     * Quote-part dans les charges communes.
     * Exemple : 1.25 → ce lot paie 1.25% des charges totales.
     */
    private BigDecimal tantieme;


    // -------------------------------------------------------------------------
    // STATUT ET PROPRIÉTAIRE
    // -------------------------------------------------------------------------

    /**
     * Statut initial du lot.
     * OCCUPE si un copropriétaire est assigné, VACANT sinon.
     * Par défaut → VACANT
     */
    private PropertyStatus status = PropertyStatus.VACANT;

    /**
     * ID du copropriétaire propriétaire de ce lot (optionnel).
     * null → lot vacant, sera attribué plus tard.
     */
    private Long ownerId;
}
