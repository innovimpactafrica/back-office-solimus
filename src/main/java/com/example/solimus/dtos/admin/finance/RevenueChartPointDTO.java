package com.example.solimus.dtos.admin.finance;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc 2 : un point du graphique "Évolution des Revenus" (mensuel/trimestriel/annuel) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueChartPointDTO {

    private String label;
    private BigDecimal amount;
}
