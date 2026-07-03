package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour la réponse du Kanban d'interventions
 * Contient les 3 colonnes avec leur count réel et items limités
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterventionKanbanResponseDTO {
    // Colonne "Signalé" (PENDING, SYNDIC_ASSIGNED, QUOTE_VALIDATED)
    private KanbanColumn reported;

    // Colonne "En cours" (STARTED)
    private KanbanColumn inProgress;

    // Colonne "Résolu" (FINISHED, FINAL_VALIDATION)
    private KanbanColumn resolved;

    /**
     * DTO imbriqué pour une colonne du Kanban
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class KanbanColumn {
        // Nombre réel total d'interventions dans cette colonne
        private Integer count;

        // Items limités à 10 éléments les plus récents
        private List<InterventionKanbanCardDTO> items;
    }
}
