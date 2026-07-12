package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

//DTO de la page de détail d'un appel exceptionnel (vue d'ensemble)
@Data
public class ExceptionalCallOverviewDTO {
    private Long id; // Identifiant de l'appel
    private String reference; // Ex: EXC-2025-001
    private String status; // BROUILLON, ACTIVE, TERMINE
    private String residenceName; // Nom de la résidence
    private String title; // Titre du projet
    private String category; // Catégorie de travaux
    private String description; // Description détaillée
    private String repartitionModeLabel; // Mode de répartition, ex: "Tantièmes" ou "Personnalisée"
    private BigDecimal totalAmount; // Montant total demandé
    private BigDecimal collectedAmount; // Montant déjà collecté
    private BigDecimal remainingAmount; // Solde restant à collecter
    private Integer collectedPercentage; // Pourcentage de recouvrement
    private Integer coOwnersCount; // Nombre de copropriétaires concernés
    private LocalDateTime createdAt; // Date de création
}
