package com.example.solimus.dtos.syndic.residence;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =============================================================================
// 
//
//  Représente un équipement commun à ajouter dans une résidence.
//  Un seul appel suffit pour tous les équipements.
//

// =============================================================================
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddFacilityDTO {

    /**
     * ID du type d'équipement commun (référence vers FacilityType).
     */
    @NotNull(message = "Le type d'équipement est obligatoire")
    private Long facilityTypeId;

    /**
     * Détails saisis librement par le syndic pour cette instance précise.
     * Exemple : "3 piscines chauffées, capacité 50 personnes chacune".
     */
    private String description;
}