package com.example.solimus.dtos.admin.withdrawal;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc 1 : KPIs de la page "Demandes de retraits" (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalDashboardKpiDTO {

    private long totalRequests;
    private Double totalRequestsEvolution;

    private long validatedRequests;
    private Double validatedRequestsEvolution;

    // Pas d'évolution (sous-titre statique "Alerte" affiché côté Front)
    private long pendingRequests;

    // Pas d'évolution (sous-titre statique "Dans 30 jours" affiché côté Front)
    private long rejectedRequests;

    // Pas d'évolution (sous-titre statique "FCFA cumulées" affiché côté Front)
    private BigDecimal totalAmount;
}