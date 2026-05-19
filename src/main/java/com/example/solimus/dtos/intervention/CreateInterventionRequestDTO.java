package com.example.solimus.dtos.intervention;

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

    @NotNull(message = "Le bien est obligatoire")
    private Long propertyId;

    @NotNull(message = "La spécialité est obligatoire")
    private Long specialtyId;

    // La liste des prestataires sélectionnés manuellement par le syndic
    @NotNull(message = "Vous devez sélectionner au moins un prestataire")
    private List<Long> targetProviderIds;

    private List<String> photoUrls;
}
