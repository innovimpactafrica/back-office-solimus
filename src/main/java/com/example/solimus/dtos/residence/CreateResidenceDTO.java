package com.example.solimus.dtos.residence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// =============================================================================
//
//  CREATE RESIDENCE DTO — Étape 1
//
//  Informations générales de la résidence.
//  La photo est uploadée séparément via multipart.
//
//  Envoyé par le front lors de la première étape du formulaire.
//  Retourne un residenceId pour les étapes suivantes.
//
// =============================================================================
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateResidenceDTO {

    // -------------------------------------------------------------------------
    // INFORMATIONS GÉNÉRALES
    // -------------------------------------------------------------------------

    /** Nom de la résidence*/
    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    /** Description générale de la résidence (optionnel) */
    private String description;


    // -------------------------------------------------------------------------
    // LOCALISATION
    // -------------------------------------------------------------------------

    /** Adresse complète. Exemple : "12 Avenue des Champs-Élysées" */
    @NotBlank(message = "L'adresse est obligatoire")
    private String fullAddress;

    /** Ville. Exemple : "Dakar" */
    @NotBlank(message = "La ville est obligatoire")
    private String city;

    /** Pays. Exemple : "Sénégal" */
    @NotBlank(message = "Le pays est obligatoire")
    private String country;

    /**
     * Latitude GPS.
     * Utilisée pour la carte et la recherche de prestataires proches.
     */
    @NotNull(message = "La latitude est obligatoire")
    private BigDecimal latitude;

    /** Longitude GPS */
    @NotNull(message = "La longitude est obligatoire")
    private BigDecimal longitude;


    // -------------------------------------------------------------------------
    // CARACTÉRISTIQUES DU BÂTIMENT (optionnel)
    // -------------------------------------------------------------------------

    /** Nombre total de lots*/
    private Integer lotsCount;

    /** Année de construction*/
    private Integer constructionYear;

    /** Année de dernière rénovation */
    private Integer renovationYear;


    // -------------------------------------------------------------------------
    // FINANCES (optionnel)
    // -------------------------------------------------------------------------

    /** Budget annuel en FCFA. Exemple : 100 000 000 */
    private BigDecimal annualBudget;
}
