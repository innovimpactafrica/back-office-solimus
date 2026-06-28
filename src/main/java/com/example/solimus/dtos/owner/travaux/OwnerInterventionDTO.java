package com.example.solimus.dtos.owner.travaux;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

// Réponse paginée de la liste des demandes de travaux côté copropriétaire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerInterventionDTO {

    // Total de toutes les demandes (tous statuts) → "Total Demandes"
    private long totalIncidents;

    // Nombre de demandes en cours (status = STARTED) → "En cours"
    private long enCoursCount;

    // Page des demandes (contenu + métadonnées de pagination)
    private Page<OwnerInterventionSummaryDTO> interventions;
}
