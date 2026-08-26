package com.example.solimus.dtos.syndic.finance;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

//DTO d'une ligne du tableau "Impayés" (module Finances)
@Data
public class UnpaidRowDTO {
    private Long chargeCallItemId;
    private String coOwnerName;
    private String propertyLabel;
    private String residenceName;
    private String period; // ex. "T3" (trimestriel) ou "Jan" (mensuel)
    private Integer year;
    private String status; // RETARD, IMPAYE
    private BigDecimal amountDue;
    private LocalDate dueDate; // colonne "Échéance" — spécifique au module Finances
    private Integer daysLate;
}