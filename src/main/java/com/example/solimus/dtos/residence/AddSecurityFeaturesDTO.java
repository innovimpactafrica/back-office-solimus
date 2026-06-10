package com.example.solimus.dtos.residence;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// =============================================================================
//
//  ADD SECURITY FEATURES DTO — Input Syndic
//
//  Permet au syndic d'ajouter des options de sécurité à une résidence
//  lors de l'étape 3 de la création.
//
// =============================================================================
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddSecurityFeaturesDTO {

    /**
     * Liste des IDs des options de sécurité à ajouter à la résidence.
     * Doit contenir au moins une option.
     */
    @NotEmpty(message = "Au moins une option de sécurité est requise")
    private List<Long> securityFeatureIds;
}
