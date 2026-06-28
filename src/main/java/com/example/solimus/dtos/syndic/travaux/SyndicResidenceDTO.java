package com.example.solimus.dtos.syndic.travaux;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO output des résidences du syndic
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyndicResidenceDTO {

    // Identifiant unique de la résidence
    private Long id;

    // Nom de la résidence (ex: "Résidence Les Jardins")
    private String name;
}
