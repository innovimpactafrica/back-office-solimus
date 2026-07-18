package com.example.solimus.dtos.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerMeetingHistoryItemDTO {

    // Date de l'AG
    private LocalDate meetingDate;

    // Titre de l'AG
    private String meetingTitle;

    // Quorum de cette AG (pourcentage pondéré par tantième)
    private Double quorumPercentage;

    // Vote de ce copropriétaire (null pour l'instant, dépend de Vote)
    private String vote;

    // Signature de présence de ce copropriétaire
    private Boolean hasSigned;
}
