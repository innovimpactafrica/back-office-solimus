package com.example.solimus.dtos.intervention;

import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.UrgencyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateInterventionRequestDTO {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotNull(message = "La résidence est obligatoire")
    private Long residenceId;

    private Long propertyId;

    @NotNull(message = "La spécialité est obligatoire")
    private Long specialtyId;

    @NotNull(message = "Le type d'incident est obligatoire")
    private IncidentLocationType locationType;

    private InterventionManagementMode managementMode;

    @NotNull(message = "Le niveau d'urgence est obligatoire")
    private UrgencyLevel urgencyLevel;

    private List<String> photoUrls;
}
