package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

//DTO d'une carte "appel de charges"
@Data
public class ChargeCallCardDTO {
    private Long id; // Identifiant de l'appel
    private String reference; // Ex: CC-2026-T1-B12
    private String status; // SOLDE, PARTIEL, ENVOYE (calculé à la volée)
    private Integer year; // Année de l'appel
    private Integer periodNumber; // Ex: 1 pour T1
    private String periodLabel; // Libellé lisible, ex: "T1 2026 (Jan-Mar)"
    private String residenceName; // Nom de la résidence
    private BigDecimal totalAmount; // Montant total de l'appel
    private Integer coOwnersCount; // Nombre de copropriétaires concernés
    private LocalDate sentDate; // Date d'envoi
    private LocalDate dueDate; // Date d'échéance
}
