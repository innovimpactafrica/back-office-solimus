package com.example.solimus.dtos.admin.dashboard;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc 2 : un point du graphique "Évolution des revenus" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyRevenueDTO {

    private int month;
    private String label;
    private BigDecimal amount;
}
