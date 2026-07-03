package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour l'étape 3 de création/modification d'une résidence
 * Contient les équipements communs et les options de sécurité
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Step3DTO {
    private List<AddFacilityDTO> facilities;      // équipements avec leurs champs dynamiques
    private List<Long> securityFeatureIds;         // IDs des options de sécurité cochées
}
