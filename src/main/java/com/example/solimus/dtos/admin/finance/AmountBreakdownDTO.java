package com.example.solimus.dtos.admin.finance;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Montant + pourcentage d'une catégorie, pour les graphiques de répartition =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmountBreakdownDTO {

    private BigDecimal amount;
    private double percentage;
}
