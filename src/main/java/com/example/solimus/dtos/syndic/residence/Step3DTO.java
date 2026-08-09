package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ============================================================================
 * ÉTAPE 3 — ÉQUIPEMENTS COMMUNS + OPTIONS DE SÉCURITÉ (résidence déjà créée)
 * ============================================================================
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Step3DTO {

    private List<AddFacilityDTO> facilities;

    private List<Long> securityFeatureIds;
}
