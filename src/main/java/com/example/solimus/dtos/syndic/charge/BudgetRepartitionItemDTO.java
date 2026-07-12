package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'une ligne de répartition d'un budget entre copropriétaires
@Data
public class BudgetRepartitionItemDTO {
    private String coOwnerName; // Nom complet du copropriétaire
    private String properties; // Ses appartements, séparés par virgules
    private BigDecimal tantieme; // Son tantième total dans cette résidence
    private BigDecimal quotePart; // Sa part du budget total, calculée via son tantième
}
