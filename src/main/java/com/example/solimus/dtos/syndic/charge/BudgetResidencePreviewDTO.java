package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aperçu retourné à l'étape 1, après sélection de la résidence.
 * Permet au syndic de visualiser qui sera concerné avant de créer le budget.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetResidencePreviewDTO {

    private Long residenceId;
    private String residenceName;
    private Integer totalProperties;      // Ex: 7 → "7 appartements"
    private BigDecimal totalTantieme;     // Doit toujours faire 100

    private List<CoOwnerTantiemePreviewDTO> coOwners;
}
