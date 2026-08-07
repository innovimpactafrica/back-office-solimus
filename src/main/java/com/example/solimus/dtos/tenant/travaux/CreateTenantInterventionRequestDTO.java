package com.example.solimus.dtos.tenant.travaux;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.UrgencyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// DTO création d'une demande de travaux côté locataire — pas de residenceId/propertyId :
// le bien du locataire connecté est déterminé automatiquement côté serveur (un seul bien par locataire).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantInterventionRequestDTO {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotNull(message = "La spécialité est obligatoire")
    private Long specialtyId;

    /**
     * ID de la partie commune concernée (obligatoire si locationType = PARTIE_COMMUNE)
     */
    private Long commonFacilityId;

    @NotNull(message = "Le type de localisation est obligatoire")
    private IncidentLocationType locationType;

    @NotNull(message = "Le niveau d'urgence est obligatoire")
    private UrgencyLevel urgencyLevel;

    private List<String> photoUrls;
}
