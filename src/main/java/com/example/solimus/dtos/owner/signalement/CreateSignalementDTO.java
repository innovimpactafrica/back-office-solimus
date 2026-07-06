package com.example.solimus.dtos.owner.signalement;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.UrgencyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSignalementDTO {

    @NotNull(message = "La résidence est obligatoire")
    private Long residenceId;

    @NotNull(message = "Le type de localisation est obligatoire")
    private IncidentLocationType locationType;

    private Long propertyId;

    private Long commonFacilityId;

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    @NotNull(message = "Le niveau d'urgence est obligatoire")
    private UrgencyLevel urgencyLevel;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    private List<String> photoUrls;
}
