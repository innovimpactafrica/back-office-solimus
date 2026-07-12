package com.example.solimus.dtos.owner.charge;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

//DTO du détail complet d'une charge (owner)
@Data
@Builder
public class MyChargeDetailDTO {
    private Long id;
    private String reference; // Ex: CHG-2026-06-A12
    private String type; // Technique : REGULAR ou EXCEPTIONAL (utilisé pour filtrer/identifier)
    private String typeLabel; // Affichage : "Charge Courante" ou "Charge Exceptionnelle"
    private BigDecimal remainingAmount; // Solde restant à payer
    private LocalDate dueDate;
    private String residenceName;
    private String propertyReference;
    private String period; // Ex: "T2 2026" ou "Juin 2026"
    private LocalDate issuedDate; // Date d'émission
    private String status;
    private String description;
    private List<ChargeBreakdownLineDTO> breakdown; // Répartition par poste
    private BigDecimal breakdownTotal; // Total de la répartition (doit égaler amount)
}
