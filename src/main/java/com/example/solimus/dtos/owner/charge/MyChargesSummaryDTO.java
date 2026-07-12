package com.example.solimus.dtos.owner.charge;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

//DTO du bandeau résumé "Mes charges" (haut de l'écran)
@Data
@Builder
public class MyChargesSummaryDTO {
    private BigDecimal totalToPay; // Somme des remainingAmount des charges filtrées non soldées
    private Integer pendingCount; // Nombre de charges filtrées non soldées
    private LocalDate nextDueDate; // Échéance la plus proche parmi les charges filtrées non soldées
}
