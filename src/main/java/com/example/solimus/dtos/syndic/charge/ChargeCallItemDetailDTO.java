package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'une ligne du tableau "Suivi par copropriétaire"
@Data
public class ChargeCallItemDetailDTO {
    private String coOwnerName; // Nom complet du copropriétaire
    private String properties; // Tous ses appartements de la résidence, séparés par virgules
    private BigDecimal tantieme; // Tantième du copropriétaire
    private BigDecimal quotePart; // Montant dû
    private BigDecimal paidAmount; // Montant payé
    private BigDecimal remainingAmount; // Solde restant
    private String status; // PAYE, PARTIEL, IMPAYE
}
