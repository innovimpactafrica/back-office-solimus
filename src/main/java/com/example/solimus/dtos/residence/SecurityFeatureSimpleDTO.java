package com.example.solimus.dtos.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =============================================================================
//
//  SECURITY FEATURE SIMPLE DTO — Output Syndic
//
//  DTO simplifié pour le syndic lors de la sélection des options de sécurité
//  pour une résidence. Contient uniquement les informations nécessaires.
//
// =============================================================================
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityFeatureSimpleDTO {

    private Long id;

    /** Label affiché. Exemple : "Vidéosurveillance" */
    private String label;
}
