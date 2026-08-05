package com.example.solimus.dtos.admin.finance;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc 3 : répartition des revenus du mois en cours entre Syndics et Prestataires =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueSplitDTO {

    private BigDecimal totalRevenue;
    private AmountBreakdownDTO syndics;
    private AmountBreakdownDTO providers;
}
