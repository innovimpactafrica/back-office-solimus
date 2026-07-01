package com.example.solimus.dtos.syndic.settings;

import com.example.solimus.enums.FacilityCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO pour créer un type d'équipement/de partie commune
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateFacilityTypeDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String name; // Nom du type d'équipement

    @NotNull(message = "La catégorie est obligatoire")
    private FacilityCategory category; // Catégorie de l'équipement (ÉQUIPEMENT, LOISIRS, SÉCURITÉ, etc.)

    private String description; // Description détaillée du type d'équipement

    private Boolean isActive; // Statut d'activation (true = disponible, false = désactivé)
}