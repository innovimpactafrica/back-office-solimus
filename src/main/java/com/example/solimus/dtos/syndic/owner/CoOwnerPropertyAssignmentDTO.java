package com.example.solimus.dtos.syndic.owner;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//DTO pour l'affectation d'un bien à un copropriétaire
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerPropertyAssignmentDTO {

    /** ID de la résidence sélectionnée dans le dropdown */
    @NotNull(message = "La résidence est obligatoire")
    private Long residenceId;

    /**
     * IDs des lots sélectionnés dans cette résidence.
     * Seuls les lots VACANT sont listés et sélectionnables.
     */
    @NotEmpty(message = "Sélectionnez au moins un lot")
    private List<Long> propertyIds;
}
