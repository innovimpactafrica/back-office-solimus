package com.example.solimus.dtos.syndic.finance;

import lombok.Data;

import java.math.BigDecimal;

//DTO d'une ligne "Paiements Récents" du dashboard Finances
@Data
public class RecentPaymentDTO {
    private String coOwnerName; // Nom du copropriétaire payeur
    private String residenceName;
    private String period; // ex. "T3" (trimestriel) ou "Jan" (mensuel)
    private Integer year;
    private String label; // Ex: "Charges"
    private String relativeTime; // Ex: "Il y a 2h"
    private BigDecimal amount; // Montant payé
}