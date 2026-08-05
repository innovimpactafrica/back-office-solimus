package com.example.solimus.dtos.admin.withdrawal;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc 3, "Analyse financière" de la fiche détail d'une demande de retrait =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalFinancialAnalysisDTO {

    private BigDecimal currentBalance;
    // Évolution vs solde de fin du mois précédent, 0% si le mois précédent vaut 0 =
    private Double evolutionPercentage;
    private BigDecimal withdrawnThisMonth;
    private BigDecimal monthlyLimit;
}