package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'une ligne du tableau "Répartition par copropriétaire" (onglet Répartition)
@Data
public class ExceptionalCallItemDetailDTO {
    private String coOwnerName; // Nom complet du copropriétaire
    private String properties; // Ses appartements, séparés par virgules
    private BigDecimal tantieme; // Son tantième dans la résidence
    private BigDecimal quotePart; // Montant qu'il doit payer
    private BigDecimal paidAmount; // Montant déjà payé
    private BigDecimal remainingAmount; // Solde restant à payer
    private String status; // PAYE, PARTIEL, IMPAYE
}
