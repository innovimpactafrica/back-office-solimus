package com.example.solimus.dtos.owner.meeting;

import lombok.*;
import java.util.List;

// ===== DTO REPONSE - ONGLET REUNION (APP MOBILE COPROPRIETAIRE) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerMeetingListResponseDTO {

    private long upcomingCount;
    private List<OwnerMeetingCardDTO> meetings;
    private long currentPage;
    private long totalPages;
}