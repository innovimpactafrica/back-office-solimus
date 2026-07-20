package com.example.solimus.dtos.owner.dashboard;

import lombok.*;
import java.math.BigDecimal;

// ===== DTO KPIS - DASHBOARD ACCUEIL COPROPRIETAIRE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerDashboardKpiDTO {

    private BigDecimal annualCharge;    // "Charge annuel"
    private BigDecimal remainingToPay;  // "Restant à payer"
}