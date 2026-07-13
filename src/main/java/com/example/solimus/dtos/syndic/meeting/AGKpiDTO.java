package com.example.solimus.dtos.syndic.meeting;

import lombok.*;

// ===== DTO KPIS DASHBOARD ASSEMBLEE GENERALE =====
// Regroupe les 4 compteurs affiches en haut du dashboard AG
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AGKpiDTO {

    private long totalCount;     // nombre total d'AG, tous statuts confondus (y compris annulees)
    private long plannedCount;   // statut UPCOMING + IN_PROGRESS
    private long completedCount; // statut COMPLETED
    private long draftCount;     // statut DRAFT
}
