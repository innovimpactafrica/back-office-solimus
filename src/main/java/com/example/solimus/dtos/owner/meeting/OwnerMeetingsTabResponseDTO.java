package com.example.solimus.dtos.owner.meeting;

import lombok.*;
import java.util.List;

// ===== DTO REPONSE GLOBALE - ONGLET AG D'UN COPROPRIETAIRE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerMeetingsTabResponseDTO {

    private double participationRate;         // taux de participation historique de CE coproprietaire (% de hasSigned=true sur ses AG terminees)

    private OwnerLastMeetingDTO lastMeeting;   // derniere AG (terminee ou la plus recente), peut etre null si aucune

    private List<OwnerMeetingHistoryRowDTO> history; // tableau "Historique des assemblees"
    private Integer totalMeetings;
    private Integer currentPage;
    private Integer totalPages;
}
