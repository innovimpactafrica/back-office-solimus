package com.example.solimus.dtos.admin.syndic;

import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc B "KPIs" de la fiche détail syndic (Admin) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicDetailKpiDTO {

    private long residencesCount;
    private long coOwnersCount;
    // Incidents (interventions) ouverts, tous statuts sauf FINAL_VALIDATION/CANCELLED
    private long openIncidentsCount;
    private long meetingDocumentsCount;
    private long meetingsCount;
    // Somme des budgets ACTIVE de toutes les résidences du syndic
    private BigDecimal totalBudgetManaged;
}
