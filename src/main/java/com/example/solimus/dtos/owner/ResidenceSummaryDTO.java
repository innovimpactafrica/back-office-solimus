package com.example.solimus.dtos.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour retourner un résumé des résidences qui ont au moins un bien vacant
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResidenceSummaryDTO {

    /** ID de la résidence */
    private Long id;

    /** Nom de la résidence */
    private String name;
}
