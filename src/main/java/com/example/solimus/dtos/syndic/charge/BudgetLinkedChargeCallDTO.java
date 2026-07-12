package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

//DTO d'un appel de charges lié à un budget précis
@Data
public class BudgetLinkedChargeCallDTO {
    private Long id;
    private String periodLabel; // Ex: "T1 2026 (Jan-Mar)"
    private BigDecimal totalAmount;
    private String status; // SOLDE, PARTIEL, ENVOYE (calculé à la volée)
    private LocalDate sentDate;
    private LocalDate dueDate;
}
