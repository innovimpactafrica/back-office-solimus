package com.example.solimus.dtos.syndic.charge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de création d'un budget prévisionnel.
 * Reçoit les 2 étapes en une seule requête.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateBudgetDTO {

    @NotNull(message = "La résidence est obligatoire")
    private Long residenceId;

    @NotNull(message = "L'année budgétaire est obligatoire")
    private Integer annee;

    @NotEmpty(message = "Au moins un poste budgétaire est requis")
    @Valid
    private List<BudgetItemInputDTO> items;
}
