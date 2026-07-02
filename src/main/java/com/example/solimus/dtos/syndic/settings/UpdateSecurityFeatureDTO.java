package com.example.solimus.dtos.syndic.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour modifier une option de sécurité
 * Tous les champs sont optionnels pour une mise à jour partielle
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSecurityFeatureDTO {

    private String label;

    private String description;

    private Boolean isActive;
}
