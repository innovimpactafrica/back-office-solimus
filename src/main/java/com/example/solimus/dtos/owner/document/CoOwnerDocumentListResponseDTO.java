package com.example.solimus.dtos.owner.document;

import lombok.*;

import java.util.List;

// ===== DTO RÉPONSE PAGINÉE - LISTE UNIFIÉE DES DOCUMENTS DU COPROPRIÉTAIRE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerDocumentListResponseDTO {

    private long totalCount;                    // nombre total de documents (toutes sources confondues)
    private List<CoOwnerDocumentDTO> documents;  // documents de la page courante
    private long currentPage;                    // page actuelle
    private long totalPages;                      // nombre total de pages
}
