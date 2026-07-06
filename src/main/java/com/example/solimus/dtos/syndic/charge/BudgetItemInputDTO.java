package com.example.solimus.dtos.syndic.charge;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Une ligne de poste budgétaire saisie par le syndic.
 * Ex: "Entretien parties communes" → 12 000 000 FCFA
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BudgetItemInputDTO {

    @NotBlank(message = "Le libellé du poste est obligatoire")
    private String libelle;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le montant doit être supérieur à 0")
    private BigDecimal montant;

    private Long commonFacilityId; // nullable — rempli si le syndic a sélectionné une suggestion
}
