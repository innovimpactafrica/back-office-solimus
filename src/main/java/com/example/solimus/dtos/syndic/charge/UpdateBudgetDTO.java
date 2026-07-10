package com.example.solimus.dtos.syndic.charge;

import com.example.solimus.enums.RepartitionMode;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de mise à jour partielle d'un budget prévisionnel.
 * Tous les champs sont optionnels pour permettre la mise à jour partielle.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateBudgetDTO {

    private Long residenceId;

    private Integer annee;

    private RepartitionMode repartitionMode;

    @Valid
    private List<BudgetItemInputDTO> items;
}
