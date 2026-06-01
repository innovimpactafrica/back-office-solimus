package com.example.solimus.dtos.charge;

import com.example.solimus.enums.ChargeStatus;
import com.example.solimus.enums.ChargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

// DTO léger pour la liste des charges du copropriétaire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeAllocationSummaryDTO {
    private Long idAllocation;
    private String reference;          // CHG-2026-06-A12
    private String title;              // "Charges mensuelles"
    private ChargeType type;           // tag "Charges courantes"
    private BigDecimal amount;         // 150 000 (sa part)
    private LocalDate dueDate;         // 15 juin 2026
    private ChargeStatus status;       // EN_ATTENTE
    private String residenceName;      // "Résidence Les Jardins"
    private String propertyReference;  // "A12"
}
