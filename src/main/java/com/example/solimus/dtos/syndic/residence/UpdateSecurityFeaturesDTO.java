package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour mettre à jour les options de sécurité d'une résidence
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSecurityFeaturesDTO {
    private List<Long> securityFeatureIds;
}
