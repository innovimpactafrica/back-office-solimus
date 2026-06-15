package com.example.solimus.dtos.charge;

import com.example.solimus.enums.ChargeStatus;
import com.example.solimus.enums.ChargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// DTO complet pour le détail d'une charge du copropriétaire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeAllocationDetailDTO {
    private Long idAllocation;
    private String reference;          // CHG-2026-06-A12
    private String title;              // "Charges mensuelles"
    private ChargeType type;           // "Charges courantes"
    private BigDecimal amount;         // 150 000 (part du copropriétaire)
    private BigDecimal totalAmount;    // 500 000 (montant total de la charge)
    private LocalDate dueDate;         // 15 juin 2026
    private ChargeStatus status;       // EN_ATTENTE
    private String period;             // "Juin 2026"
    private String residenceName;      // "Résidence Les Jardins"
    private String propertyReference;  // "A12"
    private String description;
    private List<ChargeLineDTO> lines; // répartition des frais
    private List<ChargeDocumentDTO> documents; // factures PDF
    private LocalDateTime createdAt;    // Date de création automatique
}
