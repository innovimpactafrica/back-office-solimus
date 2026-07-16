package com.example.solimus.dtos.syndic.finance;

import lombok.Data;

import java.math.BigDecimal;

//DTO d'une ligne "Paiements Récents" du dashboard Finances
@Data
public class RecentPaymentDTO {
    private String name; // Nom du copropriétaire
    private String label; // Ex: "Charges T2"
    private String relativeTime; // Ex: "Il y a 2h"
    private BigDecimal amount; // Montant payé
}
