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
public class CoOwnerMeetingsDTO {

    // Taux de participation aux AG (pourcentage)
    private Double participationRate;

    // Votes exprimés (0 pour l'instant, sera rempli quand Vote existera)
    private Integer votedCount;
    private Integer totalMeetingsCount;

    // Titre de la dernière AG
    private String lastMeetingTitle;

    // Vote à la dernière AG (null pour l'instant, dépend de Vote)
    private String lastMeetingVote;

    // Historique des AG
    private List<CoOwnerMeetingHistoryItemDTO> meetingHistory;
}
