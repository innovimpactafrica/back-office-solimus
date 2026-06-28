package com.example.solimus.dtos.syndic.residence;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// =============================================================================
//
//  CREATE SECURITY FEATURE DTO — Input Admin
//
//  Permet à l'admin de créer une nouvelle option de sécurité
//  qui sera disponible pour tous les syndics.
//
// =============================================================================
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateSecurityFeatureDTO {

    /**
     * Label de l'option.
     */
    @NotBlank(message = "Le label est obligatoire")
    private String label;

    /**
     * Description optionnelle.
     */
    private String description;
}
