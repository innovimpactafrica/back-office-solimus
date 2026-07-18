package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.util.List;

// ===== DTO REPONSE - ONGLET PARTICIPANTS D'UNE AG =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipantsTabResponseDTO {

    private long totalCount;    // "Tous "
    private long presentCount;  // "Présent "
    private long absentCount;   // "Absent "

    private List<MeetingParticipantRowDTO> participants;
    private long currentPage;
    private long totalPages;
}
