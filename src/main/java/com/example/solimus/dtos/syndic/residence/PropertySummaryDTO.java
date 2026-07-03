package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO résumé d'un lot (Property)
 * Utilisé pour afficher les lots d'un copropriétaire dans le tableau des appels de charges
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PropertySummaryDTO {
    // Référence du lot (ex: "A-101")
    private String reference;

    // Type de bien (ex: "Appartement", "Bureau", etc.)
    private String typeName;
}
