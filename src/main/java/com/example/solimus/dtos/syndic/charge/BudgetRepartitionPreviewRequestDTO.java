package com.example.solimus.dtos.syndic.charge;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de requête pour prévisualiser la répartition annuelle/par période AVANT la création du
 * budget — écran "Nouveau Budget Prévisionnel" (Étape 2 — Postes budgétaires). Aucune donnée
 * n'est enregistrée, uniquement calculée à la volée avec exactement la même méthode que la
 * création réelle (ChargeAllocationUtil.distributeByLargestRemainder).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BudgetRepartitionPreviewRequestDTO {

    @Valid
    private List<BudgetItemInputDTO> items;
}
