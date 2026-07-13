package com.example.solimus.dtos.syndic.meeting;

import java.util.List;
import lombok.*;

// ===== DTO REPONSE PAGINEE - LISTING ASSEMBLEES GENERALES =====
// Suit le meme pattern que PaymentListResponse / SyndicSignalementListResponse
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AGListResponseDTO {

    private AGKpiDTO kpis;            // les 4 compteurs du haut de page (Total, Planifiees, Terminees, Brouillons)

    private Integer totalMeetings;    // nombre total d'AG correspondant aux filtres (avant pagination)
    private List<AGCardDTO> meetings; // liste des cartes AG pour la page courante

    private Integer currentPage;      // page actuelle
    private Integer totalPages;       // nombre total de pages
}
