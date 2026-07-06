package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

//DTO pour l'onglet Finances du détail copropriétaire (par résidence)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerFinancesDTO {

    // 5 cards KPI
    private BigDecimal annualCharges;
    private BigDecimal monthlyCharges;
    private BigDecimal currentBalance;
    private BigDecimal paymentsMade;
    private Double paymentsPercentage;
    private BigDecimal remainingToBill;

    // Répartition des charges (donut)
    private List<ChargeBreakdownItemDTO> chargeBreakdown;

    // Historique des paiements (graphique mensuel)
    private List<MonthlyPaymentDTO> monthlyPayments;

    // Tableau des appels de charges
    private List<ChargeCallRowDTO> chargeCalls;
}
