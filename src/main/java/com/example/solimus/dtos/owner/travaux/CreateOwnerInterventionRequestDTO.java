package com.example.solimus.dtos.owner.travaux;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.UrgencyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//Dto création intervention côté owner
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOwnerInterventionRequestDTO {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotNull(message = "La résidence est obligatoire")
    private Long residenceId;

    private Long propertyId;

    /**
     * ID de la partie commune concernée (obligatoire si locationType = PARTIE_COMMUNE)
     */
    private Long commonFacilityId;

    @NotNull(message = "La spécialité est obligatoire")
    private Long specialtyId;

    @NotNull(message = "Le type de localisation est obligatoire")
    private IncidentLocationType locationType;

    private InterventionManagementMode managementMode;

    @NotNull(message = "Le niveau d'urgence est obligatoire")
    private UrgencyLevel urgencyLevel;

    private List<String> photoUrls;
}
