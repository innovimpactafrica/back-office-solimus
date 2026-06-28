package com.example.solimus.dtos.syndic.settings;

import com.example.solimus.enums.FacilityCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// DTO pour le listing / affichage des types d'équipement
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FacilityTypeDTO {
    private Long id; // Identifiant du type d'équipement
    private String name; // Nom du type d'équipement
    private FacilityCategory category; // Catégorie (ÉQUIPEMENT, LOISIRS, etc.)
    private String categoryLabel; // Libellé de la catégorie en français
    private String icon; // Icône associée
    private String description; // Description du type
    private Boolean isActive; // Actif ou désactivé

    // Nombre de résidences utilisant ce type d'équipement (calculé)
    private int residenceCount;

    // Liste des champs applicables pour ce type (pour le frontend)
    private List<String> fields;
}