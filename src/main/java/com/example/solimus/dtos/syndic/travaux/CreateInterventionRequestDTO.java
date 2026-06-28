package com.example.solimus.dtos.syndic.travaux;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.UrgencyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// DTO pour créer une demande de travaux (requête entrante)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateInterventionRequestDTO {

    @NotBlank(message = "Le titre est obligatoire")
    private String title; // Titre de l'intervention

    @NotBlank(message = "La description est obligatoire")
    private String description; // Description détaillée du problème

    @NotNull(message = "La résidence est obligatoire")
    private Long residenceId; // ID de la résidence concernée

    private Long propertyId; // ID du bien (appartement/local) - optionnel si c'est une partie commune

    private Long commonFacilityId; // ID de la partie commune - obligatoire si locationType = PARTIE_COMMUNE

    @NotNull(message = "La spécialité est obligatoire")
    private Long specialtyId; // ID de la spécialité requise (Plomberie, Électricité, etc.)

    @NotNull(message = "Le type de localisation est obligatoire")
    private IncidentLocationType locationType; // Type de localisation (APPARTEMENT ou PARTIE_COMMUNE)

    private InterventionManagementMode managementMode; // Mode de gestion (SYNDIC, OWNER)

    @NotNull(message = "Le niveau d'urgence est obligatoire")
    private UrgencyLevel urgencyLevel; // Niveau d'urgence (BAS, MOYEN, URGENT)

    private List<String> photoUrls; // Liste des URLs des photos uploadées
}