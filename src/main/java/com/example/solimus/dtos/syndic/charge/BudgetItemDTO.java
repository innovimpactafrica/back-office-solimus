package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Une ligne de poste budgétaire.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BudgetItemDTO {

    private Long id;
    private String libelle;
    private BigDecimal montant;
}
