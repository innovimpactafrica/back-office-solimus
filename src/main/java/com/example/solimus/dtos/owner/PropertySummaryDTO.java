package com.example.solimus.dtos.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour retourner un résumé des biens disponibles
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PropertySummaryDTO {

    /** ID du bien */
    private Long id;

    /** Référence du bien (ex: A-101) */
    private String reference;
}
