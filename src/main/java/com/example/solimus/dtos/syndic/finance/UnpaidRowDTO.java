package com.example.solimus.dtos.syndic.finance;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

//DTO d'une ligne du tableau "Impayés" (module Finances)
@Data
public class UnpaidRowDTO {
    private Long chargeCallItemId;
    private String coOwnerName;
    private String residenceName; // Ajouté — colonne "Résidence" séparée
    private BigDecimal amountDue;
    private LocalDate dueDate; // Ajouté — colonne "Échéance"
    private Integer daysLate;
    private String status; // RETARD, CRITIQUE
}
