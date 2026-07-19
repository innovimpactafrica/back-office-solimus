package com.example.solimus.dtos.syndic.owner;

import lombok.*;
import java.util.List;

// ===== DTO RÉPONSE PAGINÉE - DOCUMENTS D'UN COPROPRIÉTAIRE (TOUTES SOURCES CONFONDUES) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerDocumentUnifiedListResponseDTO {

    private long totalCount;                          // nombre total de documents (toutes sources confondues)
    private List<CoOwnerDocumentUnifiedDTO> documents; // documents de la page courante
    private long currentPage;                           // page actuelle
    private long totalPages;                             // nombre total de pages
}