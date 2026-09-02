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

    // Nombre d'AG où ce copropriétaire a signé sa présence (présent ou procuration)
    private Integer votedCount;
    private Integer totalMeetingsCount;

    // Titre de la dernière AG
    private String lastMeetingTitle;

    // Historique des AG
    private List<CoOwnerMeetingHistoryItemDTO> meetingHistory;
}
