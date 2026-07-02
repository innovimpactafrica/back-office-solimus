package com.example.solimus.dtos.syndic.settings;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour créer une option de sécurité
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateSecurityFeatureDTO {

    @NotBlank(message = "Le label est obligatoire")
    private String label;

    private String description;

    private Boolean isActive;
}
