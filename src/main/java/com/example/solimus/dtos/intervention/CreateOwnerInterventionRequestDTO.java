package com.example.solimus.dtos.intervention;

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
public class CreateOwnerInterventionRequestDTO {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotNull(message = "La spécialité est obligatoire")
    private Long specialtyId;

    @NotNull(message = "Les prestataires ciblés sont obligatoires")
    private List<Long> targetProviderIds;

    private List<String> photoUrls;
}
