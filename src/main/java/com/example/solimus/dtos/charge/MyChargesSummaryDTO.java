package com.example.solimus.dtos.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// DTO pour le dashboard du copropriétaire — résumé + liste des charges
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyChargesSummaryDTO {
    private BigDecimal totalAPayer;        // Total des charges en attente
    private int chargesEnAttente;          // Nombre de charges en attente
    private LocalDate prochaineEcheance;   // Prochaine date d'échéance
    private List<ChargeAllocationSummaryDTO> charges; // Liste des charges (paginée)
    private int totalPages;                // Nombre total de pages
    private long totalElements;            // Nombre total d'éléments
    private int currentPage;               // Page actuelle
}
