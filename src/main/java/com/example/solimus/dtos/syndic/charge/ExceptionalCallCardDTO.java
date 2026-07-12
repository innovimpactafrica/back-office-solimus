package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

//DTO d'une carte "appel exceptionnel"
@Data
public class ExceptionalCallCardDTO {
    private Long id; // Identifiant de l'appel
    private String reference; // Ex: EXC-2025-001
    private String title; // Ex: "Réfection toiture terrasse"
    private String status; // BROUILLON, ACTIVE, TERMINE
    private String residenceName; // Nom de la résidence
    private BigDecimal totalAmount; // Montant total demandé
    private BigDecimal collectedAmount; // Montant déjà collecté
    private BigDecimal remainingAmount; // Solde restant à collecter
    private Integer collectedPercentage; // Pourcentage de recouvrement
    private Integer coOwnersCount; // Nombre de copropriétaires concernés
    private LocalDateTime activatedAt; // Date d'activation
}
