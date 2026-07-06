package com.example.solimus.dtos.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipantsResponseDTO {

    // Nombre total de participants (convocés)
    private Integer totalCount;

    // Nombre de présents
    private Integer presentCount;

    // Nombre d'absents
    private Integer absentCount;

    // Nombre de procurations
    private Integer proxyCount;

    // Liste des participants (filtrée si status param fourni)
    private List<MeetingParticipantRowDTO> participants;
}
