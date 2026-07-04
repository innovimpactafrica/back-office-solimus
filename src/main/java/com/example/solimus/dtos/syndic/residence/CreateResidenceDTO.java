package com.example.solimus.dtos.syndic.residence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ============================================================================
 * ÉTAPE 1 — CRÉATION D'UNE RÉSIDENCE (infos générales)
 * ============================================================================
 * Envoyé dans la partie "data" d'une requête multipart/form-data,
 * accompagné du fichier "photo".
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateResidenceDTO {

    private String name;              // "Nom de la Résidence"
    private String description;       // "Description"

    private String fullAddress;       // "Numéro et rue"
    private String city;              // "Ville"
    private String country;           // "Pays"

    private BigDecimal latitude;      // fourni par l'autocomplete front
    private BigDecimal longitude;

    private LocalDate constructionDate; // Date de construction complète
    private LocalDate renovationDate;   // Date de rénovation complète, optionnel

    private List<ContactInputDTO> contacts; // "Contact clé" — un ou plusieurs (bouton "+ Contact")
}
