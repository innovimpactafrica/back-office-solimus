package com.example.solimus.dtos.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerInterventionsResponseDTO {

    // Nombre d'interventions actives (non résolues)
    private Integer activeCount;

    // Liste des interventions
    private List<CoOwnerInterventionRowDTO> interventions;
}
