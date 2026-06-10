package com.example.solimus.dtos.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =============================================================================
//
//  SECURITY FEATURE DTO — Output
//
// =============================================================================
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityFeatureDTO {

    private Long id;

    /** Label affiché. Exemple : "Vidéosurveillance" */
    private String label;

    /** Description optionnelle */
    private String description;

    /**
     * Indique si cette option est active et visible pour les syndics.
     */
    private boolean active;
}
