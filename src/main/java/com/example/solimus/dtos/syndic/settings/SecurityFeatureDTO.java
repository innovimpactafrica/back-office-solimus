package com.example.solimus.dtos.syndic.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour le listing / affichage des options de sécurité
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityFeatureDTO {
    private Long id;
    private String label;
    private String description;
    private Boolean active;
    private String icon;
}
